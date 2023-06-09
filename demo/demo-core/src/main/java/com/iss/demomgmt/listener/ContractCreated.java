package com.iss.demomgmt.listener;


import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.core.operations.document.BlockPermissionInheritance;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventListener;

import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.runtime.api.Framework;

public class ContractCreated implements EventListener {
  

    @Override
    public void handleEvent(Event event) {
        EventContext ctx = event.getContext();
        if (!(ctx instanceof DocumentEventContext)) {
          return;
        }

        DocumentEventContext docCtx = (DocumentEventContext) ctx;
        DocumentModel doc = docCtx.getSourceDocument();

        // Add some logic starting from here.
        if (doc.getType().equals("Contract")) {
            OperationContext opCtx = new OperationContext(docCtx.getCoreSession());
            opCtx.setInput(doc);
            AutomationService as = Framework.getService(AutomationService.class);
            try {
                as.run(opCtx, BlockPermissionInheritance.ID);
            } catch (OperationException e) {
                e.printStackTrace();
            }
        }
    }
}
