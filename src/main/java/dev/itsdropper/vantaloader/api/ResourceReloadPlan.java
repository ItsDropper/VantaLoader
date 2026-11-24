package dev.itsdropper.vantaloader.api;

/**
 * Prepared resource replacement. Prepare all affected components before committing any;
 * rollback must restore the state that was active before prepare/commit.
 */
public interface ResourceReloadPlan {
    void commit() throws Exception;
    void rollback() throws Exception;
}
