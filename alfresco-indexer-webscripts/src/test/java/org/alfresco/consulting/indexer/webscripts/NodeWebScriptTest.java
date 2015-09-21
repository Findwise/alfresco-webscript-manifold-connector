package org.alfresco.consulting.indexer.webscripts;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.web.scripts.BaseWebScriptTest;
import org.alfresco.service.cmr.rating.RatingService;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.tagging.TaggingService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.util.ApplicationContextHelper;
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
    protected RatingService ratingService;
    protected TaggingService taggingService;

    private static final String STORE_PROTOCOL = "workspace";
    private static final String STORE_ID = "SpacesStore";

    @BeforeClass
    public void setUp() throws Exception {
        ApplicationContextHelper.setUseLazyLoading(false);
        ApplicationContextHelper.setNoAutoStart(true);
        nodeService = getServer().getApplicationContext().getBean("NodeService", NodeService.class);
        namespaceService = getServer().getApplicationContext().getBean("NamespaceService", NamespaceService.class);
        transactionService = getServer().getApplicationContext().getBean("TransactionService", TransactionService.class);
        contentService = getServer().getApplicationContext().getBean("ContentService", ContentService.class);
        ratingService = getServer().getApplicationContext().getBean("RatingService", RatingService.class);
        taggingService = getServer().getApplicationContext().getBean("TaggingService", TaggingService.class);
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
        
        // create some tags
        taggingService.addTag(nodeRef, "tag1");
        taggingService.addTag(nodeRef, "tag2");
        
        // add a like
        ratingService.applyRating(nodeRef, 1.0f, "likesRatingScheme");
        
        // add some comments
        addComment(nodeRef, "this is the first comment");
        addComment(nodeRef, "this is the second comment");

        //Get (and assert) the uuid details
        String detailsUrl = String.format("/node/details/%s/%s/%s",
                STORE_PROTOCOL,
                STORE_ID,
                nodeRef.getId());
        response = sendRequest(new TestWebScriptServer.GetRequest(detailsUrl), 200);
        result = new JSONObject(response.getContentAsString());
        System.out.println(result.toString());
        assertNodeDetails(result, nodeRef.getId());

        //Testing /auth/resolve Webscript
        response = sendRequest(new TestWebScriptServer.GetRequest("/auth/resolve/admin"), 200);
        JSONArray resultList = new JSONArray(response.getContentAsString());
        assertAdminAuthResolve(resultList);

        response = sendRequest(new TestWebScriptServer.GetRequest("/auth/resolve/"), 200);
        resultList = new JSONArray(response.getContentAsString());
        assertAdminAuthResolve(resultList);
    }

    private void addComment(NodeRef nodeRef, String comment) throws JSONException, IOException, UnsupportedEncodingException {
      JSONObject json = new JSONObject();
      json.put("content", comment);
      json.put("itemTitle", "Foobar");
      json.put("page", "document-details");
      json.put("pageParams", "{\"nodeRef\":\"" + nodeRef + "\"}");
      
      String addCommentUrl = String.format("/api/node/%s/%s/%s/comments", STORE_PROTOCOL, STORE_ID, nodeRef.getId());
      sendRequest(new TestWebScriptServer.PostRequest(addCommentUrl, json.toString(), "application/json; charset=utf-8"), 200);
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
        
        JSONArray tags = result.getJSONArray("tags");
        assertEquals(2, tags.length());
        for (int x = 0; x < tags.length(); x++) {
          String tag = tags.getString(x);
          assertNotNull(tag);
          assertTrue(tag.length() > 0);
        }
        
        int likes = result.getInt("likes");
        assertEquals(1, likes);
        
        JSONArray comments = result.getJSONArray("comments");
        assertEquals(2, comments.length());
        for (int x = 0; x < comments.length(); x++) {
          String comment = comments.getString(x);
          assertNotNull(comment);
          assertTrue(comment.length() > 0);
        }
    }
}