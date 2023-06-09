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
@Operation(id = GetVocabularyLabel.ID, category = Constants.CAT_DOCUMENT, label = "Get Vocabulary Label", description = "Describe here what your operation does.")
public class GetVocabularyLabel {

    public static final String ID = "Document.GetVocabularyLabel";
    private final static Log log = LogFactory.getLog(GetVocabularyLabel.class);

    @Context
    protected CoreSession session;

    @Param(name = "voc", required = false)
    protected String voc;

    @Param(name = "key", required = false)
    protected String key;

    @OperationMethod
    public String run() throws OperationException, IOException {
        PlatformFunctions Fn = new PlatformFunctions();
        String[] keys = key.split("/");
        for (int i = 0; i < keys.length; i++) {
            String label = Fn.getVocabularyLabel(voc, keys[i]);
            keys[i] = label;
        }

        String result = StringUtils.join(keys, "/");
        return result;
    }
}
