package com.iss.demomgmt.operation;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.common.utils.i18n.I18NUtils;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.automation.core.util.Properties;
import org.nuxeo.ecm.automation.features.SuggestConstants;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.io.registry.MarshallingConstants;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.Schema;
import org.nuxeo.ecm.directory.Directory;
import org.nuxeo.ecm.directory.DirectoryException;
import org.nuxeo.ecm.directory.Session;
import org.nuxeo.ecm.directory.api.DirectoryService;
import org.nuxeo.ecm.directory.io.DirectoryEntryJsonWriter;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.api.Framework;

import java.io.IOException;
import java.io.Serializable;
import java.text.Collator;
import java.util.*;

/**
 *
 */
@Operation(id = PermissionDirectoryEntries.ID, category = Constants.CAT_SERVICES, label = "Get permission dirctory entries", description = "Describe here what your operation does.")
public class PermissionDirectoryEntries {

    /**
     * @since 5.9.3
     */
    Collator collator;

    /**
     * Convenient class to build JSON serialization of results.
     *
     * @since 5.7.2
     */
    @Context
    protected CoreSession session;

    @Context
    protected UserManager userManager;

    private static final Logger log = LogManager.getLogger(PermissionDirectoryEntries.class);

    @Param(name = "showChildrenOnly", required = false)
    protected boolean showChildrenOnly = false;

    private class JSONAdapter implements Comparable<JSONAdapter> {

        private static final String DEPTH = "depth";

        private final Map<String, JSONAdapter> children;

        private final Directory directory;

        private boolean isRoot;

        private Boolean isLeaf = null;

        private final boolean isChained;

        private final String parentDirectoryName;

        private final boolean hasParentDirectory;

        private Map<String, Object> obj;

        public JSONAdapter(Directory directory) {
            this.directory = directory;
            this.parentDirectoryName = directory.getParentDirectory();
            this.hasParentDirectory = hasParentDirectory(parentDirectoryName, directory.getName());
            isChained = isChainedDirectory(directory);
            this.children = new HashMap<>();
            // We are the root node
            this.isRoot = true;
        }

        public JSONAdapter(Directory directory, DocumentModel entry) {
            this(directory);
            // Carry entry, not root
            isRoot = false;
            // build JSON object for this entry
            obj = new LinkedHashMap<>();
            Map<String, Object> properties = new LinkedHashMap<>();
            for (Map.Entry<String, Field> e : directory.getSchemaFieldMap().entrySet()) {
                String key = e.getKey();
                Field field = e.getValue();
                Serializable value = entry.getPropertyValue(field.getName().getPrefixedName());
                if (value != null) {
                    if (label.equals(key)) {
                        if (localize && !dbl10n) {
                            // translations are in messages*.properties files
                            value = translate(value.toString());
                        }
                        obj.put(SuggestConstants.LABEL, value);
                    }
                    obj.put(key, value);
                    properties.put(key, value);
                }
            }
            if (displayObsoleteEntries && obj.containsKey(SuggestConstants.OBSOLETE_FIELD_ID)
                    && Integer.parseInt(obj.get(SuggestConstants.OBSOLETE_FIELD_ID).toString()) > 0) {
                obj.put(SuggestConstants.WARN_MESSAGE_LABEL, getObsoleteWarningMessage());
            }
            obj.put("directoryName", directory.getName());
            obj.put("properties", properties);
            obj.put(MarshallingConstants.ENTITY_FIELD_NAME, DirectoryEntryJsonWriter.ENTITY_TYPE);
        }

        @Override
        public int compareTo(JSONAdapter other) {
            if (other != null) {
                int i = this.getOrder() - other.getOrder();
                if (i != 0) {
                    return i;
                } else {
                    return getCollator().compare(this.getLabel(), other.getLabel());
                }
            } else {
                return -1;
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            JSONAdapter other = (JSONAdapter) obj;
            if (!getOuterType().equals(other.getOuterType())) {
                return false;
            }
            if (this.obj == null) {
                if (other.obj != null) {
                    return false;
                }
            } else if (!this.obj.equals(other.obj)) {
                return false;
            }
            return true;
        }

        public List<Map<String, Object>> getChildrenJSONArray() {
            List<Map<String, Object>> result = new ArrayList<>();
            for (JSONAdapter ja : getSortedChildren()) {
                // When serializing in JSON, we are now able to COMPUTED_ID
                // which is the chained path of the entry (i.e absolute path
                // considering its ancestor)
                ja.getObj()
                        .put(SuggestConstants.COMPUTED_ID, (!isRoot ? (getComputedId() + keySeparator) : "") + ja.getId());
                ja.getObj()
                        .put(SuggestConstants.ABSOLUTE_LABEL,
                                (!isRoot ? (getAbsoluteLabel() + absoluteLabelSeparator) : "") + ja.getLabel());
                Map<String, Object> jo = ja.toJSONObject(ja);
                if (jo != null) {
                    result.add(jo);
                }
            }
            return result;
        }

        public String getComputedId() {
            return isRoot ? null : obj.getOrDefault(SuggestConstants.COMPUTED_ID, "").toString();
        }

        public String getId() {
            return isRoot ? null : obj.getOrDefault(SuggestConstants.ID, "").toString();
        }

        public String getDepth() {
            return obj.getOrDefault(DEPTH, "0").toString();
        }

        public String getLabel() {
            return isRoot ? null : obj.getOrDefault(SuggestConstants.LABEL, "").toString();
        }

        public String getAbsoluteLabel() {
            return isRoot ? null : obj.getOrDefault(SuggestConstants.ABSOLUTE_LABEL, "").toString();
        }

        public Map<String, Object> getObj() {
            return obj;
        }

        public int getOrder() {
            return isRoot ? -1
                    : Integer.parseInt(obj.getOrDefault(SuggestConstants.DIRECTORY_ORDER_FIELD_NAME, "0").toString());
        }

        private PermissionDirectoryEntries getOuterType() {
            return PermissionDirectoryEntries.this;
        }

        public String getParentId() {
            return isRoot ? null : obj.getOrDefault(SuggestConstants.PARENT_FIELD_ID, "").toString();
        }

        public List<JSONAdapter> getSortedChildren() {
            if (children == null) {
                //return List.of();
                return null;
            }
            List<JSONAdapter> result = new ArrayList<>(children.values());
            Collections.sort(result);
            return result;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getOuterType().hashCode();
            result = prime * result + ((obj == null) ? 0 : obj.hashCode());
            return result;
        }

        /**
         * Does the associated vocabulary / directory entry have child entries.
         *
         * @return true if it has children
         * @since 5.7.2
         */
        public boolean isLeaf() {
            if (isLeaf == null) {
                if (isChained && !hasParentDirectory) {
                    // hierarchical directory
                    String id = getId();
                    if (id != null) {
                        Map<String, Serializable> filter = Collections.singletonMap(SuggestConstants.PARENT_FIELD_ID,
                                getId());
                        try (Session s = directory.getSession()) {
                            isLeaf = s.query(filter, Collections.emptySet(), Collections.emptyMap(), false, 1, -1)
                                    .isEmpty();
                        } catch (DirectoryException ce) {
                            log.error("Could not retrieve children of entry", ce);
                            isLeaf = true;
                        }
                    } else {
                        isLeaf = true;
                    }
                } else {
                    // otherwise rely on the children
                    isLeaf = children.isEmpty();
                }
            }
            return isLeaf;
        }

        private void mergeJsonAdapter(JSONAdapter branch) {
            JSONAdapter found = children.get(branch.getId());
            if (found != null) {
                for (JSONAdapter branchChild : branch.children.values()) {
                    found.mergeJsonAdapter(branchChild);
                }
            } else {
                children.put(branch.getId(), branch);
            }
        }

        public JSONAdapter push(final JSONAdapter newEntry) {
            String parentIdOfNewEntry = newEntry.getParentId();
            if (parentIdOfNewEntry != null && !parentIdOfNewEntry.isEmpty()) {
                // The given adapter has a parent which could already be in my
                // descendants
                if (parentIdOfNewEntry.equals(this.getId()) && (!newEntry.hasParentDirectory
                        || newEntry.parentDirectoryName.equals(this.directory.getName()))) {
                    // this is the parent. We must insert the given adapter
                    // here. We merge all its
                    // descendants
                    mergeJsonAdapter(newEntry);
                    return this;
                } else {
                    // I am not the parent, let's check if I could be the
                    // parent
                    // of one the ancestor.
                    JSONAdapter parentAdapter = getParentAdapter(newEntry);
                    if (parentAdapter == null) {
                        log.info("parent: {} not found for entry: {}", newEntry::getParentId, newEntry::getId);
                        mergeJsonAdapter(newEntry);
                        return this;
                    } else {
                        return push(parentAdapter.push(newEntry));
                    }
                }
            } else {
                // The given adapter has no parent, I can merge it in my
                // descendants.
                mergeJsonAdapter(newEntry);
                return this;
            }
        }

        private JSONAdapter getParentAdapter(JSONAdapter entry) {
            String parentId = entry.getParentId();
            Directory d = entry.hasParentDirectory
                    ? Framework.getService(DirectoryService.class).getDirectory(entry.parentDirectoryName)
                    : entry.directory;
            try (Session session = d.getSession()) {
                DocumentModel parent = session.getEntry(parentId);
                return parent != null ? new JSONAdapter(d, parent) : null;
            }
        }

        private Map<String, Object> toJSONObject() {
            if (isLeaf()) {
                return getObj();
            } else {
                // This entry has sub entries in the directory.
                // Ruled by Select2: an optionGroup is selectable or not
                // whether
                // we provide an Id or not in the JSON object.
                if (canSelectParent) {
                    // Make it selectable, keep as it is
                    Map<String, Object> o = getObj();
                    o.put("children", getChildrenJSONArray());
                    return o;
                } else {
                    // We don't want it to be selectable, we just serialize the
                    // label
                    Map<String, Object> o = new LinkedHashMap<>();
                    o.put(SuggestConstants.LABEL, getLabel());
                    o.put("children", getChildrenJSONArray());
                    return o;
                }
            }
        }

        private Map<String, Object> toJSONObject(JSONAdapter entry) {
//            log.warn(Arrays.toString(departments));
            NuxeoPrincipal principal = session.getPrincipal();
            // if is administrators group, can select all items
            if (principal.isMemberOf("administrators")) {
                if (isLeaf()) {
                    return getObj();
                } else {
                    // Make it selectable, keep as it is
                    Map<String, Object> o = getObj();
                    o.put("children", getChildrenJSONArray());
                    return o;
                }
            } else {
                if (canSelectEntry(entry)) {
//                    log.warn("can select:" + getLabel());
                    Map<String, Object> o = getObj();
                    if (!isLeaf()) {
                        o.put("children", getChildrenJSONArray());
                    }
                    return o;
                } else {
//                    log.warn("can not select:" + getLabel());
                    if (showEntry()) {
                        Map<String, Object> o = new LinkedHashMap<>();
                        o.put(SuggestConstants.LABEL, getLabel());
                        o.put(DEPTH, getDepth());
//                        log.warn("depth:" + getDepth());
                        if (!isLeaf()) {
                            o.put("children", getChildrenJSONArray());
                        }
                        return o;
                    } else {
                        return null;
                    }
                }
            }
        }

        private Boolean canSelectEntry(JSONAdapter entry) {
//            log.warn(getComputedId());
//            log.warn(entry);
            if (StringUtils.isNotEmpty(getParentId())) {
//                log.warn(getParentId());
//                log.warn(getParentAdapter(entry));
//                log.warn(getParentAdapter(entry).getId());
            }

            if (showChildrenOnly) {
                if (StringUtils.isNotEmpty(getParentId()) && isAdminGroup(getParentComputedId())
                        && Arrays.asList(departments).contains(getParentComputedId())) {
                    return true;
                }
            }

            if (!showChildrenOnly) {
                if ((Arrays.asList(departments).contains(getComputedId()) && isAdminGroup(getComputedId()))
                        || (StringUtils.isNotEmpty(getParentId()) && isAdminGroup(getParentComputedId())
                        && Arrays.asList(departments).contains(getParentComputedId()))) {
                    return true;
                }
            }

            return false;
        }

        private Boolean isAdminGroup(String department) {
            NuxeoPrincipal principal = session.getPrincipal();
            List<String> groups = principal.getAllGroups();
            for (String groupname : groups) {
                DocumentModel group = userManager.getGroupModel(groupname);
                if (group.getPropertyValue("group:department") != null
                        && group.getPropertyValue("group:department").equals(department)
                        && group.getPropertyValue("group:groupType") != null
                        && group.getPropertyValue("group:groupType").equals("Admin")) {
                    return true;
                }
            }
            return false;
        }

        private String getParentComputedId() {
            String parentId = getParentId();
            String computedId = getComputedId();
            Integer index = computedId.indexOf(parentId);
            String parentComputedId = computedId.substring(0, index) + parentId;
//            log.warn(parentComputedId);
            return parentComputedId;
        }

        private Boolean showEntry() {
            for (String department : departments) {
                if (department.indexOf(getComputedId()) != -1) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return String.valueOf(obj);
        }

    }


    public static final String ID = "Directory.PermissionEntries";

    @Context
    protected OperationContext ctx;

    @Context
    protected DirectoryService directoryService;

    @Context
    protected SchemaManager schemaManager;

    @Param(name = "directoryName", required = true)
    protected String directoryName;

    @Param(name = "localize", required = false)
    protected boolean localize;

    @Param(name = "lang", required = false)
    protected String lang;

    @Param(name = "departments", required = true)
    protected String[] departments;

    @Param(name = "searchTerm", alias = "prefix", required = false)
    protected String prefix;

    @Param(name = "labelFieldName", required = false)
    protected String labelFieldName = SuggestConstants.DIRECTORY_DEFAULT_LABEL_COL_NAME;

    @Param(name = "dbl10n", required = false)
    protected boolean dbl10n = false;

    @Param(name = "canSelectParent", required = false)
    protected boolean canSelectParent = false;

    @Param(name = "filterParent", required = false)
    protected boolean filterParent = false;

    @Param(name = "keySeparator", required = false)
    protected String keySeparator = SuggestConstants.DEFAULT_KEY_SEPARATOR;

    @Param(name = "displayObsoleteEntries", required = false)
    protected boolean displayObsoleteEntries = false;

    /**
     * @since 10.2
     */
    @Param(name = "filters", required = false)
    protected org.nuxeo.ecm.automation.core.util.Properties filters = new Properties();

    /**
     * @since 8.2
     */
    @Param(name = "limit", required = false)
    protected int limit = -1;

    /**
     * Fetch mode. If not contains, then starts with.
     *
     * @since 5.9.2
     */
    @Param(name = "contains", required = false)
    protected boolean contains = false;

    /**
     * Choose if sort is case sensitive
     *
     * @since 5.9.3
     */
    @Param(name = "caseSensitive", required = false)
    protected boolean caseSensitive = false;

    /**
     * Separator to display absolute label
     *
     * @since 5.9.2
     */
    @Param(name = "absoluteLabelSeparator", required = false)
    protected String absoluteLabelSeparator = "/";

    private String label = null;

    private String obsoleteWarningMessage = null;

    protected String getLang() {
        if (lang == null) {
            lang = (String) ctx.get("lang");
            if (lang == null) {
                lang = SuggestConstants.DEFAULT_LANG;
            }
        }
        return lang;
    }

    protected Locale getLocale() {
        return new Locale(getLang());
    }

    /**
     * @since 5.9.3
     */
    protected Collator getCollator() {
        if (collator == null) {
            collator = Collator.getInstance(getLocale());
            if (caseSensitive) {
                collator.setStrength(Collator.TERTIARY);
            } else {
                collator.setStrength(Collator.SECONDARY);
            }
        }
        return collator;
    }

    protected String getObsoleteWarningMessage() {
        if (obsoleteWarningMessage == null) {
            obsoleteWarningMessage = I18NUtils.getMessageString("messages", "obsolete", new Object[0], getLocale());
        }
        return obsoleteWarningMessage;
    }

    @OperationMethod
    public Blob run() throws IOException {
        Directory directory = directoryService.getDirectory(directoryName);
        if (directory == null) {
            log.error("Could not find directory with name: {}", directoryName);
            return null;
        }
        try (Session session = directory.getSession()) {
            Schema schema = schemaManager.getSchema(directory.getSchema());
            label = SuggestConstants.getLabelFieldName(schema, dbl10n, labelFieldName, getLang());
            Map<String, Serializable> filter = new HashMap<>(filters);
            if (!displayObsoleteEntries) {
                // Exclude obsolete
                filter.put(SuggestConstants.OBSOLETE_FIELD_ID, Long.valueOf(0));
            }
            boolean postFilter = true;
            Set<String> fullText = new TreeSet<>();
            if (dbl10n || !localize) {
                postFilter = false;
                // do the filtering at directory level
                if (prefix != null && !prefix.isEmpty()) {
                    String computedPrefix = prefix;
                    if (contains) {
                        computedPrefix = '%' + computedPrefix;
                    }
                    filter.put(label, computedPrefix);
                    fullText.add(label);
                }
            }

            // when post filtering we need to get all entries
            DocumentModelList entries = session.query(filter, fullText, Collections.emptyMap(), false,
                    postFilter ? -1 : limit, -1);

            boolean isChained = isChainedDirectory(directory);
            boolean hasParentDirectory = hasParentDirectory(directory.getParentDirectory(), directoryName);
            boolean pFilter = postFilter; // effectively final
            JSONAdapter jsonAdapter = new JSONAdapter(directory);
            entries.stream()
                    .map(entry -> new JSONAdapter(directory, entry))
                    .filter(adapter -> filterJSONAdapter(adapter, isChained, hasParentDirectory, pFilter))
                    .forEach(jsonAdapter::push);
            return Blobs.createJSONBlobFromValue(jsonAdapter.getChildrenJSONArray());
        }
    }

    private boolean filterJSONAdapter(JSONAdapter adapter, boolean isChained, boolean hasParentDirectory,
                                      boolean postFilter) {
        if (!filterParent && isChained && !hasParentDirectory && !adapter.isLeaf()) {
            // only called on hierarchical directories
            return false;
        }

        if (StringUtils.isNotEmpty(prefix) && postFilter) {
            if (contains) {
                return adapter.getLabel().toLowerCase().contains(prefix.toLowerCase());
            } else {
                return adapter.getLabel().toLowerCase().startsWith(prefix.toLowerCase());
            }
        }
        return true;
    }

    protected String translate(final String key) {
        if (key == null) {
            return "";
        }
        return I18NUtils.getMessageString("messages", key, new Object[0], getLocale());
    }

    private boolean isChainedDirectory(Directory directory) {
        String schemaName = directory.getSchema();
        Schema schema = schemaManager.getSchema(schemaName);
        Field parentField = schema.getField(SuggestConstants.PARENT_FIELD_ID);
        return parentField != null;
    }

    private boolean hasParentDirectory(String parentDirectoryName, String directoryName) {
        return StringUtils.isNotBlank(parentDirectoryName) && !parentDirectoryName.equals(directoryName);
    }
}
