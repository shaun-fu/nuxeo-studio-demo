package com.iss.demomgmt.listener;


import com.iss.demomgmt.operation.UpdateContractState;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.runtime.api.Framework;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class ContractCanceled implements EventListener {

    private final static Log log = LogFactory.getLog(ContractCanceled.class);

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
            log.info(String.format("Contract (%s) has been cancelled.", doc.getId()));

            OperationContext operationContext = new OperationContext(ctx.getCoreSession());
            operationContext.setInput(doc);
            AutomationService automationService = Framework.getService(AutomationService.class);
            try {
                Map<String, Serializable> params = new HashMap<>(1);
                params.put("transition", "to_cancelled");
                automationService.run(operationContext, UpdateContractState.ID, params);
            } catch (OperationException e) {
                e.printStackTrace();
                log.error(e);
            }
        }
    }
}
