package com.iss.demomgmt.operation;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.automation.features.PlatformFunctions;
import org.nuxeo.ecm.core.api.CoreSession;

import java.io.IOException;

/**
 *
 */
@Operation(id = GetVocabularyLabels.ID, category = Constants.CAT_DOCUMENT, label = "Get Vocabulary Labels", description = "Describe here what your operation does.")
public class GetVocabularyLabels {

    public static final String ID = "Document.GetVocabularyLabels";
    private final static Log log = LogFactory.getLog(GetVocabularyLabel.class);

    @Context
    protected CoreSession session;

    @Param(name = "voc", required = false)
    protected String voc;

    @Param(name = "keys", required = false)
    protected String[] keys;

    @OperationMethod
    public String[] run() throws OperationException, IOException {
        PlatformFunctions Fn = new PlatformFunctions();
        String[] results = new String[keys.length];
        for (int index = 0; index < keys.length; index++) {
            String key = keys[index];
            String[] splits = key.split("/");
            for (int i = 0; i < splits.length; i++) {
                String label = Fn.getVocabularyLabel(voc, splits[i]);
                splits[i] = label;
            }
            String joins = StringUtils.join(splits, "/");
            results[index] = joins;
        }

        return results;
    }
}
