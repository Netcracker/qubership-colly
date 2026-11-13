package org.qubership.colly;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class GitPushRejectedExceptionMapper implements ExceptionMapper<GitPushRejectedException> {

    @Override
    public Response toResponse(GitPushRejectedException exception) {
        return Response
                .status(Response.Status.CONFLICT)
                .entity(exception.getMessage())
                .build();
    }
}
