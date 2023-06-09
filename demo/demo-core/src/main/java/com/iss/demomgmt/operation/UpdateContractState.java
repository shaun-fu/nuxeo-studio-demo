package com.iss.demomgmt.operation;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;

/**
 *
 */
@Operation(id = UpdateContractState.ID, category = Constants.CAT_DOCUMENT, label = "Update Contract State", description = "Describe here what your operation does.")
public class UpdateContractState {

    public static final String ID = "Document.UpdateContractState";
    private final static Log log = LogFactory.getLog(UpdateContractState.class);

    @Context
    protected CoreSession session;

    @Param(name = "transition", required = true)
    protected String transition;

    @OperationMethod
    public DocumentModel run(DocumentModel doc) {
        boolean res = session.followTransition(doc, transition);
        if (res) {
            String state = session.getCurrentLifeCycleState(doc.getRef());
            log.info(String.format("Document (%s) state changed to %s.", doc.getId(), state));
        }
        return doc;
    }
}
