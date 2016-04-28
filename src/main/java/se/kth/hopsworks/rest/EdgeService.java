package se.kth.hopsworks.rest;


import org.apache.commons.beanutils.BeanUtils;
import org.codehaus.jackson.map.ObjectMapper;
import se.kth.hopsworks.controller.ResponseMessages;
import se.kth.hopsworks.filters.AllowedRoles;
import se.kth.hopsworks.workflows.*;

import javax.annotation.security.RolesAllowed;
import javax.ejb.EJB;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.enterprise.context.RequestScoped;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@RequestScoped
@RolesAllowed({"SYS_ADMIN", "BBC_USER"})
@TransactionAttribute(TransactionAttributeType.NEVER)
public class EdgeService {
    private final static Logger logger = Logger.getLogger(EdgeService.class.
            getName());


    @EJB
    private EdgeFacade edgeFacade;

    @EJB
    private NoCacheResponse noCacheResponse;

    public void setWorkflow(Workflow workflow) {
        this.workflow = workflow;
    }

    private Workflow workflow;

    public EdgeService(){

    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @AllowedRoles(roles = {AllowedRoles.DATA_SCIENTIST, AllowedRoles.DATA_OWNER})
    public Response index() throws AppException {
        Collection<Edge> edges = workflow.getEdges();
        GenericEntity<Collection<Edge>> edgesList = new GenericEntity<Collection<Edge>>(edges) {};
        return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(edgesList).build();
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @AllowedRoles(roles = {AllowedRoles.DATA_SCIENTIST, AllowedRoles.DATA_OWNER})
    public Response create(
            Edge edge,
            @Context HttpServletRequest req) throws AppException {
        edge.setWorkflowId(workflow.getId());
        edgeFacade.persist(edge);
        return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(edge).build();

    }

    @GET
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @AllowedRoles(roles = {AllowedRoles.DATA_SCIENTIST, AllowedRoles.DATA_OWNER})
    public Response show(
            @PathParam("id") String id) throws AppException {
        EdgePK edgePk = new EdgePK(id, workflow.getId());
        Edge edge = edgeFacade.findById(edgePk);
        if (edge == null) {
            throw new AppException(Response.Status.BAD_REQUEST.getStatusCode(),
                    ResponseMessages.EDGE_NOT_FOUND);
        }
        return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(edge).build();
    }

    @PUT
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @AllowedRoles(roles = {AllowedRoles.DATA_SCIENTIST, AllowedRoles.DATA_OWNER})
    public Response update(
            String stringParams,
            @PathParam("id") String id
    ) throws AppException, IllegalAccessException, InvocationTargetException {
        EdgePK edgePk = new EdgePK(id, workflow.getId());
        Edge edge = edgeFacade.findById(edgePk);
        if (edge == null) {
            throw new AppException(Response.Status.BAD_REQUEST.getStatusCode(),
                    ResponseMessages.EDGE_NOT_FOUND);
        }
        Map<String, Object> paramsMap = new ObjectMapper().convertValue(stringParams, Map.class);
        BeanUtils.populate(edge, paramsMap);
        edge = edgeFacade.merge(edge);

        return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(edge).build();
    }

    @DELETE
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @AllowedRoles(roles = {AllowedRoles.DATA_SCIENTIST, AllowedRoles.DATA_OWNER})
    public Response delete(
            @PathParam("id") String id) throws AppException {
        EdgePK edgePk = new EdgePK(id, workflow.getId());
        Edge edge = edgeFacade.findById(edgePk);
        if (edge == null) {
            throw new AppException(Response.Status.BAD_REQUEST.getStatusCode(),
                    ResponseMessages.EDGE_NOT_FOUND);
        }
        edgeFacade.remove(edge);
        return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).build();
    }
}