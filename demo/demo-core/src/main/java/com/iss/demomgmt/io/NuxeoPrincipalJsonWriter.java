package com.iss.demomgmt.io;

import com.fasterxml.jackson.core.JsonGenerator;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoGroup;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.io.marshallers.json.ExtensibleEntityJsonWriter;
import org.nuxeo.ecm.core.io.marshallers.json.OutputStreamWithJsonWriter;
import org.nuxeo.ecm.core.io.registry.Writer;
import org.nuxeo.ecm.core.io.registry.reflect.Setup;
import org.nuxeo.ecm.directory.api.DirectoryService;
import org.nuxeo.ecm.platform.usermanager.UserManager;

import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static org.nuxeo.ecm.core.io.registry.reflect.Instantiations.SINGLETON;
import static org.nuxeo.ecm.core.io.registry.reflect.Priorities.REFERENCE;

@Setup(mode = SINGLETON, priority = REFERENCE)
public class NuxeoPrincipalJsonWriter extends ExtensibleEntityJsonWriter<NuxeoPrincipal> {

    public static final String ENTITY_TYPE = "user";

    @Inject
    private UserManager userManager;

    @Inject
    private DirectoryService directoryService;

    public NuxeoPrincipalJsonWriter() {
        super(ENTITY_TYPE, NuxeoPrincipal.class);
    }

    @Override
    protected void writeEntityBody(NuxeoPrincipal principal, JsonGenerator jg) throws IOException {
        jg.writeStringField("id", principal.getName());
        writeProperties(jg, principal);
        writeExtendedGroups(jg, principal);
        jg.writeBooleanField("isAdministrator", principal.isAdministrator());
        jg.writeBooleanField("isAnonymous", principal.isAnonymous());
    }

    private void writeProperties(JsonGenerator jg, NuxeoPrincipal principal) throws IOException {
        DocumentModel doc = principal.getModel();
        if (doc == null) {
            return;
        }
        String userSchema = userManager.getUserSchemaName();
        Collection<Property> properties = doc.getPropertyObjects(userSchema);
        if (properties.isEmpty()) {
            return;
        }
        Writer<Property> propertyWriter = registry.getWriter(ctx, Property.class, APPLICATION_JSON_TYPE);
        jg.writeObjectFieldStart("properties");
        for (Property property : properties) {
            String localName = property.getField().getName().getLocalName();
            if (!localName.equals(getPasswordField())) {
                jg.writeFieldName(localName);
                OutputStream out = new OutputStreamWithJsonWriter(jg);
                propertyWriter.write(property, Property.class, Property.class, APPLICATION_JSON_TYPE, out);
            }
        }
        jg.writeEndObject();
    }

    private void writeExtendedGroups(JsonGenerator jg, NuxeoPrincipal principal) throws IOException {
        jg.writeArrayFieldStart("extendedGroups");
        for (String strGroup : principal.getAllGroups()) {
            NuxeoGroup group = userManager.getGroup(strGroup);
            String label = group == null ? strGroup : group.getLabel();
            DocumentModel groupModel = group.getModel();

            jg.writeStartObject();
            jg.writeStringField("name", strGroup);
            jg.writeStringField("label", label);
            jg.writeStringField("url", "group/" + strGroup);
            // write extra fields
            jg.writeStringField("department", (String) groupModel.getPropertyValue("group:department"));
            jg.writeStringField("groupType", (String) groupModel.getPropertyValue("group:groupType"));
            jg.writeEndObject();
        }
        jg.writeEndArray();
    }

    private String getPasswordField() {
        String userDirectoryName = userManager.getUserDirectoryName();
        return directoryService.getDirectory(userDirectoryName).getPasswordField();
    }
}
