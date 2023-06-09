package com.iss.demomgmt.listener;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.core.operations.document.AddPermission;
import org.nuxeo.ecm.automation.core.operations.document.ReplacePermission;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.PostCommitFilteringEventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.runtime.api.Framework;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class PortfolioDocumentCreated implements PostCommitFilteringEventListener {

    protected final List<String> handled = Arrays.asList("documentCreated");

    private final static Log log = LogFactory.getLog(PortfolioDocumentCreated.class);

    @Override
    public void handleEvent(EventBundle events) {
        for (Event event : events) {
            if (acceptEvent(event)) {
                handleEvent(event);
            }
        }
    }

    @Override
    public boolean acceptEvent(Event event) {
        return handled.contains(event.getName());
    }


    public void handleEvent(Event event) {
        EventContext ctx = event.getContext();
        if (!(ctx instanceof DocumentEventContext)) {
            return;
        }

        DocumentEventContext docCtx = (DocumentEventContext) ctx;
        DocumentModel doc = docCtx.getSourceDocument();

        // Add some logic starting from here.
        if (doc.getType().equals("Portfolio")) {
            try {
                OperationContext opCtx = new OperationContext(docCtx.getCoreSession());
                opCtx.setInput(doc);
                AutomationService as = Framework.getService(AutomationService.class);

                //Grant Everything Permission to Sub-Admin Group And Block Permission Inheritance
                String adminGroup = (String) doc.getPropertyValue("pt:adminGroup");
                log.warn(adminGroup);
                List<String> users = new ArrayList<String>();
                users.add(adminGroup);

                HashMap<String, Serializable> params = new HashMap<>();
                params.put("permission", "Everything");
                params.put("users", (Serializable) users);
                params.put("blockInheritance", true);
                as.run(opCtx, AddPermission.ID, params);

                //Replace current user permission with Read
                NuxeoPrincipal principal = docCtx.getCoreSession().getPrincipal();
                String userName = principal.getName();
                log.warn(userName);
                if (!principal.isAdministrator() && !principal.isMemberOf("administrators")) {
                    //hig_admin:Everything:true:hig_admin::
                    String aceId = String.format("%s:Everything:true:%s::", userName, userName);
                    log.warn(aceId);
                    params = new HashMap<>();
                    params.put("id", aceId);
                    params.put("permission", "Read");
                    params.put("username", userName);
                    as.run(opCtx, ReplacePermission.ID, params);
                }

                //Grant Read Permission to members Group And Block Permission Inheritance
                users = new ArrayList<String>();
                users.add("members");
                params = new HashMap<>();
                params.put("permission", "Read");
                params.put("users", (Serializable) users);
                params.put("blockInheritance", true);
                as.run(opCtx, AddPermission.ID, params);
            } catch (OperationException e) {
                log.error(e, e);
            }
        }
    }
}
