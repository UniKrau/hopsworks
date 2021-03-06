/*
 * Copyright (C) 2013 - 2018, Logical Clocks AB and RISE SICS AB. All rights reserved
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS  OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL  THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR  OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package io.hops.hopsworks.common.hive;

import io.hops.hopsworks.common.dao.dataset.Dataset;
import io.hops.hopsworks.common.dao.dataset.DatasetPermissions;
import io.hops.hopsworks.common.dao.dataset.DatasetFacade;
import io.hops.hopsworks.common.dao.dataset.DatasetType;
import io.hops.hopsworks.common.dao.hdfs.inode.Inode;
import io.hops.hopsworks.common.dao.hdfs.inode.InodeFacade;
import io.hops.hopsworks.common.dao.hdfsUser.HdfsUsers;
import io.hops.hopsworks.common.dao.project.Project;
import io.hops.hopsworks.common.dao.project.ProjectFacade;
import io.hops.hopsworks.common.dao.user.Users;
import io.hops.hopsworks.common.hdfs.DistributedFileSystemOps;
import io.hops.hopsworks.common.hdfs.HdfsUsersController;
import io.hops.hopsworks.common.security.BaseHadoopClientsService;
import io.hops.hopsworks.common.util.Settings;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsAction;
import org.apache.hadoop.fs.permission.FsPermission;

import javax.annotation.PreDestroy;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

@Stateless(name = "HiveController")
public class HiveController {

  @EJB
  private Settings settings;
  @EJB
  private HdfsUsersController hdfsUsersBean;
  @EJB
  private InodeFacade inodeFacade;
  @EJB
  private DatasetFacade datasetFacade;
  @EJB
  private BaseHadoopClientsService bhcs;
  @EJB
  private ProjectFacade projectFacade;

  private final static String driver = "org.apache.hive.jdbc.HiveDriver";
  private final static Logger logger = Logger.getLogger(HiveController.class.getName());

  private Connection conn;
  private String jdbcString = null;

  private void initConnection() throws SQLException{
    try {
      // Load Hive JDBC Driver
      Class.forName(driver);

      // Create connection url
      String hiveEndpoint = settings.getHiveServerHostName(false);
      jdbcString = "jdbc:hive2://" + hiveEndpoint + "/default;" +
          "auth=noSasl;ssl=true;twoWay=true;" +
          "sslTrustStore=" + bhcs.getSuperTrustStorePath() + ";" +
          "trustStorePassword=" + bhcs.getSuperTrustStorePassword() + ";" +
          "sslKeyStore=" + bhcs.getSuperKeystorePath() + ";" +
          "keyStorePassword=" + bhcs.getSuperKeystorePassword();

      conn = DriverManager.getConnection(jdbcString);
    } catch (ClassNotFoundException e) {
      logger.log(Level.SEVERE, "Error opening Hive JDBC connection: " +
        e);
    }
  }

  @PreDestroy
  public void close() {
    try {
      if (conn != null && !conn.isClosed()){
        conn.close();
      }
    } catch (SQLException e) {
      logger.log(Level.WARNING, "Error closing Hive JDBC connection: " +
        e);
    }
  }

  @TransactionAttribute(TransactionAttributeType.NEVER)
  public void createDatabase(Project project, Users user, DistributedFileSystemOps dfso)
      throws SQLException, IOException {
    if (conn == null || conn.isClosed()) {
      initConnection();
    }

    Statement stmt = null;
    try {
      // Create database
      stmt = conn.createStatement();
      // Project name cannot include any spacial character or space.
      stmt.executeUpdate("create database " + project.getName());
    } finally {
      if (stmt != null) {
        stmt.close();
      }
    }

    // Hive database names are case insensitive and lower case
    Path dbPath = getDbPath(project.getName());
    Inode dbInode = inodeFacade.getInodeAtPath(dbPath.toString());

    // Persist Hive db as dataset in the HopsWorks database
    Dataset dbDataset = new Dataset(dbInode, project);
    dbDataset.setType(DatasetType.HIVEDB);
    // As we are running Zeppelin as projectGenericUser, we have to make
    // the directory editable by default
    dbDataset.setEditable(DatasetPermissions.GROUP_WRITABLE_SB);
    dbDataset.setDescription(buildDescription(project.getName()));
    datasetFacade.persistDataset(dbDataset);

    try {
      // Assign database directory to the user and project group
      hdfsUsersBean.addDatasetUsersGroups(user, project, dbDataset, dfso);

      // Make the dataset editable by default
      FsPermission fsPermission = new FsPermission(FsAction.ALL, FsAction.ALL,
          FsAction.NONE, true);
      dfso.setPermission(dbPath, fsPermission);

      // Set the default quota
      dfso.setHdfsSpaceQuotaInMBs(dbPath, settings.getHiveDbDefaultQuota());
      projectFacade.setTimestampQuotaUpdate(project, new Date());
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Cannot assign Hive database directory " + dbPath.toString() +
          " to correct user/group. Trace: " + e);

      // Remove the database directory and cleanup the metadata
      try {
        dfso.rm(dbPath, true);
      } catch (IOException rmEx) {
        // Nothing we can really do here
        logger.log(Level.SEVERE, "Cannot delete Hive database directory: " + dbPath.toString() +
          " Trace: " + rmEx);
      }

      throw new IOException(e);
    }
  }

  public void dropDatabase(Project project, DistributedFileSystemOps dfso, boolean forceCleanup)
      throws IOException {
    // To avoid case sensitive bugs, check if the project has a Hive database
    Dataset ds = datasetFacade.findByNameAndProjectId(project, project.getName().toLowerCase() + ".db");
    
    if ((ds == null || ds.getType() != DatasetType.HIVEDB)
        && !forceCleanup)  {
      return;
    }

    // Delete HopsFs db directory -- will automatically clean up all the related Hive's metadata
    dfso.rm(getDbPath(project.getName()), true);

    // Delete all the scratchdirs
    for (HdfsUsers u : hdfsUsersBean.getAllProjectHdfsUsers(project.getName())) {
      dfso.rm(new Path(settings.getHiveScratchdir(), u.getName()), true);
    }
  }

  public Path getDbPath(String projectName) {
    return new Path(settings.getHiveWarehouse(), projectName.toLowerCase() + ".db");
  }

  private String buildDescription(String projectName) {
    return "Use the following configuration settings to connect to Hive from external clients:<br>" +
        "Url: jdbc:hive2://" + settings.getHiveServerHostName(true) + "/" + projectName + "<br>" +
        "Authentication: noSasl<br>" +
        "SSL: enabled - TrustStore and its password<br>" +
        "Username: your HopsWorks email address<br>" +
        "Password: your HopsWorks password";
  }


}
