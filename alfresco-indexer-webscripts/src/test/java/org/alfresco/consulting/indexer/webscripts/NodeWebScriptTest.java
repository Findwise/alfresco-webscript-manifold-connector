package org.alfresco.consulting.indexer.webscripts;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.web.scripts.BaseWebScriptTest;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.util.ApplicationContextHelper;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.extensions.webscripts.TestWebScriptServer;
import org.springframework.extensions.webscripts.TestWebScriptServer.Response;

public class NodeWebScriptTest extends BaseWebScriptTest {

    protected NodeService nodeService;
    protected NamespaceService namespaceService;
    protected ApplicationContext applicationContext;
    protected TransactionService transactionService;
    protected ContentService contentService;

    private static final String STORE_PROTOCOL = "workspace";
    private static final String STORE_ID = "SpacesStore";

    private static Logger log = Logger.getLogger(NodeWebScriptTest.class);

    @BeforeClass
    public void setUp() throws Exception {
        ApplicationContextHelper.setUseLazyLoading(false);
        ApplicationContextHelper.setNoAutoStart(true);
        nodeService = (NodeService) super.getServer().getApplicationContext().getBean("NodeService");
        namespaceService = (NamespaceService) super.getServer().getApplicationContext().getBean("NamespaceService");
        transactionService = (TransactionService) super.getServer().getApplicationContext().getBean("TransactionService");
        contentService = super.getServer().getApplicationContext().getBean("ContentService", ContentService.class);
    }

    @Test
    public void testNodeChangesAndDetails() throws Exception {
        AuthenticationUtil.setFullyAuthenticatedUser(AuthenticationUtil.getAdminUserName());
        setDefaultRunAs("admin");
        String changesUrl = String.format("/node/changes/%s/%s",
                STORE_PROTOCOL,
                STORE_ID);

        //Get (and assert) all node changes
        Response response = sendRequest(new TestWebScriptServer.GetRequest(changesUrl), 200);
        JSONObject result = new JSONObject(response.getContentAsString());
        assertNodeChanges(result);

        //Find the uuid of a cm:content, not being deleted and that is part of an Alfresco Share site
        JSONArray docs = result.getJSONArray("docs");
        
        // make sure that there's no XML documents in the result set
        assertNoXml(docs);
        
        NodeRef nodeRef = null;
        for (int i = 0; i < docs.length() - 1; i++) {
            JSONObject doc = docs.getJSONObject(i);
            String type = doc.get("type").toString();
            String deleted = doc.get("deleted").toString();
            String uuid = doc.get("uuid").toString();
            nodeRef = new NodeRef(STORE_PROTOCOL, STORE_ID, uuid);
            if (type.equals("cm:content") && deleted.equals("false")) {
                if (nodeService.exists(nodeRef)) {
                    String nodePath = nodeService.getPath(nodeRef).toPrefixString(namespaceService);
                    if (nodePath.contains("sites")) {
                        break;
                    }
                }
            }
        }

        //Get (and assert) the uuid details
        String detailsUrl = String.format("/node/details/%s/%s/%s",
                STORE_PROTOCOL,
                STORE_ID,
                nodeRef.getId());
        response = sendRequest(new TestWebScriptServer.GetRequest(detailsUrl), 200);
        result = new JSONObject(response.getContentAsString());
        assertNodeDetails(result, nodeRef.getId());

        //Testing /auth/resolve Webscript
        response = sendRequest(new TestWebScriptServer.GetRequest("/auth/resolve/admin"), 200);
        JSONArray resultList = new JSONArray(response.getContentAsString());
        assertAdminAuthResolve(resultList);

        response = sendRequest(new TestWebScriptServer.GetRequest("/auth/resolve/"), 200);
        resultList = new JSONArray(response.getContentAsString());
        assertAdminAuthResolve(resultList);
    }

    private void assertNoXml(JSONArray docs) throws JSONException {
      // make sure that there's no XML documents in the result...
      for (int i = 0; i < docs.length() - 1; i++) {
        JSONObject doc = docs.getJSONObject(i);
        String uuid = doc.getString("uuid");
        NodeRef document = new NodeRef(STORE_PROTOCOL, STORE_ID, uuid);
        
        if (!nodeService.exists(document)) {
          continue;
        }
        
        ContentReader reader = contentService.getReader(document, ContentModel.PROP_CONTENT);
        if (reader == null) {
          continue;
        }
        
        String mimetype = reader.getMimetype();
        if ("text/xml".equalsIgnoreCase(mimetype)) {
          fail("There shouldn't be any XML files in the result set!");
        }
      }        
    }

    private void assertAdminAuthResolve(JSONArray resultList) throws Exception {
        for (int j = 0; j < resultList.length() - 1; j++) {
            JSONObject result = resultList.getJSONObject(j);
            String username = result.get("username").toString();
            assertNotNull(username);
            JSONArray auths = result.getJSONArray("authorities");
            assertTrue(auths.length() > 0);
            if (!username.equals("guest")) {
                boolean everyoneGroupFound = false;
                for (int i = 0; i < auths.length() - 1; i++) {
                    String auth = auths.get(i).toString();
                    if (auth.equals("GROUP_EVERYONE")) {
                        everyoneGroupFound = true;
                        break;
                    }
                }
                assertTrue(everyoneGroupFound);
            }
        }
    }

    public void assertNodeChanges(JSONObject result) throws Exception {
        assertNotNull(result);

        String storeId = result.get("store_id").toString();
        assertEquals("SpacesStore", storeId);

        String storeProtocol = result.get("store_protocol").toString();
        assertEquals("workspace", storeProtocol);

        Integer lastTxn = new Integer(result.get("last_txn_id").toString());
        assertTrue(lastTxn > 0);

        Integer lastAcl = new Integer(result.get("last_acl_changeset_id").toString());
        assertTrue(lastAcl > 0);

        JSONArray docs = result.getJSONArray("docs");
        for (int i = 0; i < docs.length() - 1; i++) {
            JSONObject doc = docs.getJSONObject(i);
            String type = doc.get("type").toString();
            assertNotNull(type);
            String uuid = doc.get("uuid").toString();
            assertNotNull(uuid);
            String propertiesUrl = doc.get("propertiesUrl").toString();
            assertNotNull(propertiesUrl);
            assertTrue(propertiesUrl.contains(uuid));
            String deleted = doc.get("deleted").toString();
            assertNotNull(new Boolean(deleted));
        }
    }

    public void assertNodeDetails(JSONObject result, String uuid) throws Exception {
        assertNotNull(result);

        JSONArray authorities = result.getJSONArray("readableAuthorities");
        assertNotSame(authorities.length(), 0);
        String path = result.get("path").toString();
        assertNotNull(path);
        String shareUrlPath = result.get("shareUrlPath").toString();
        assertTrue(shareUrlPath.contains(uuid));
        assertTrue(shareUrlPath.contains("http"));
        String contentUrlPath = result.get("contentUrlPath").toString();
        assertTrue(contentUrlPath.contains(uuid));
        assertTrue(contentUrlPath.contains("http"));
        JSONArray aspects = result.getJSONArray("aspects");
        assertTrue(aspects.length() > 0);
        JSONArray properties = result.getJSONArray("properties");
        assertTrue(properties.length() > 0);
        for (int i = 0; i < properties.length() - 1; i++) {
            JSONObject prop = properties.getJSONObject(i);
            String name = prop.get("name").toString();
            assertNotNull(name);
            String type = prop.get("type").toString();
            assertNotNull(type);
            String value = prop.get("value").toString();
            assertNotNull(value);
        }
    }
}