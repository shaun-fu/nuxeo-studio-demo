package com.iss.demomgmt.operation;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.automation.core.operations.notification.SendMail;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.runtime.api.Framework;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 *
 */
@Operation(id = SendContractApprovedNotification.ID, category = Constants.CAT_NOTIFICATION, label = "Send Contract Approved Notification to User", description = "Describe here what your operation does.")
public class SendContractApprovedNotification {

    public static final String ID = "Notification.SendContractApprovedNotification";
    private final static Log log = LogFactory.getLog(SendContractApprovedNotification.class);

    @Context
    protected CoreSession session;

    @Param(name = "to", required = true)
    protected String to;

    @OperationMethod
    public DocumentModel run(DocumentModelList docs) throws OperationException {
        DocumentModel doc = docs.get(0);
        if (!StringUtils.isBlank(to) && doc.getType().equals("Contract")) {
            String state = session.getCurrentLifeCycleState(doc.getRef());
            log.info(state);
            AutomationService as = Framework.getService(AutomationService.class);
            OperationContext ctx = new OperationContext(session);
            ctx.setInput(doc);
            Map<String, Serializable> params = new HashMap<>();
            log.info(Framework.getProperty("mail.from"));
            params.put("from", Framework.getProperty("mail.from"));
            log.info(to);
            params.put("to", to);
            params.put("message", "template:contract-approved");
            params.put("subject", String.format("%s has been approved", doc.getTitle()));
            params.put("HTML", true);

            as.run(ctx, SendMail.ID, params);

        }

        return doc;
    }
}
