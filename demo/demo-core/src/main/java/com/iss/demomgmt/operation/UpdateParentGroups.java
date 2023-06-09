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
import org.nuxeo.ecm.platform.usermanager.UserManager;

import java.util.List;

/**
 *
 */
@Operation(id = UpdateParentGroups.ID, category = Constants.CAT_USERS_GROUPS, label = "Update Parent Groups", description = "Describe here what your operation does.")
public class UpdateParentGroups {

    public static final String ID = "Group.UpdateParentGroups";
    protected static final Log logger = LogFactory.getLog(UpdateParentGroups.class);

    @Context
    protected CoreSession session;

    @Context
    protected UserManager userManager;

    @Param(name = "groupname", required = false)
    protected String groupname;

    @Param(name = "powerusers", required = false)
    protected boolean powerusers = false;

    @OperationMethod
    public DocumentModel run() {
        DocumentModel groupModel = userManager.getGroupModel(groupname);
        List<String> parentGroups = (List<String>) groupModel.getPropertyValue("parentGroups");
        if (!parentGroups.contains("members")) {
            parentGroups.add("members");
        }
        if (powerusers && !parentGroups.contains("powerusers")) {
            parentGroups.add("powerusers");
        }
        if (!powerusers && parentGroups.contains("powerusers")) {
            parentGroups.remove("powerusers");
        }
        groupModel.setPropertyValue("parentGroups", parentGroups.toArray());
        userManager.updateGroup(groupModel);
        return userManager.getGroupModel(groupname);
    }
}
