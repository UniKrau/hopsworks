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

package io.hops.hopsworks.dela.cluster;

import io.hops.hopsworks.common.dao.dataset.Dataset;
import io.hops.hopsworks.common.dao.dataset.DatasetFacade;
import io.hops.hopsworks.common.dao.log.operation.OperationType;
import io.hops.hopsworks.common.dataset.DatasetController;
import java.util.List;
import java.util.logging.Logger;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

@Stateless
@TransactionAttribute(TransactionAttributeType.NEVER)
public class ClusterDatasetController {

  private Logger logger = Logger.getLogger(ClusterDatasetController.class.getName());

  @EJB
  private DatasetController datasetCtrl;
  @EJB
  private DatasetFacade datasetFacade;

  public Dataset shareWithCluster(Dataset dataset) {
    if (dataset.isPublicDs()) {
      return dataset;
    }

    for (Dataset d : datasetFacade.findByInode(dataset.getInode())) {
      d.setPublicDsState(Dataset.SharedState.CLUSTER);
      datasetFacade.merge(d);
      datasetCtrl.logDataset(d, OperationType.Update);
    }
    return dataset;
  }

  public Dataset unshareFromCluster(Dataset dataset) {
    if (!dataset.isPublicDs()) {
      return dataset;
    }

    for (Dataset d : datasetFacade.findByInode(dataset.getInode())) {
      d.setPublicDsState(Dataset.SharedState.PRIVATE);
      datasetFacade.merge(d);
      datasetCtrl.logDataset(d, OperationType.Update);
    }
    return dataset;
  }

  public List<Dataset> getPublicDatasets() {
    return datasetFacade.findAllDatasetsByState(Dataset.SharedState.CLUSTER.state, false);
  }
}
