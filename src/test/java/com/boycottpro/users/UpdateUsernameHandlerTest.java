package com.boycottpro.users;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.boycottpro.models.ResponseMessage;
import com.boycottpro.users.models.UserForm;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UpdateUsernameHandlerTest {

    @Mock
    private DynamoDbClient dynamoDb;

    @Mock
    private Context context;

    @InjectMocks
    private UpdateUsernameHandler handler;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testHandleRequest_successfulUsernameChange() throws Exception {
        String userId = "user123";
        String oldUsername = "old_name";
        String newUsername = "new_name";

        UserForm form = new UserForm();
        form.setUser_id(null);
        form.setOldUsername(oldUsername);
        form.setNewUsername(newUsername);

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        Map<String, String> claims = Map.of("sub", "11111111-2222-3333-4444-555555555555");
        Map<String, Object> authorizer = new HashMap<>();
        authorizer.put("claims", claims);

        APIGatewayProxyRequestEvent.ProxyRequestContext rc = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        rc.setAuthorizer(authorizer);
        event.setRequestContext(rc);

        // Path param "s" since client calls /users/s
        event.setPathParameters(Map.of("user_id", "s"));
        event.setBody(objectMapper.writeValueAsString(form));

        Map<String, AttributeValue> userItem = Map.of("username", AttributeValue.fromS(oldUsername));
        GetItemResponse getResponse = GetItemResponse.builder().item(userItem).build();
        when(dynamoDb.getItem(any(GetItemRequest.class))).thenReturn(getResponse);

        UpdateItemResponse updateResponse = UpdateItemResponse.builder().build();
        when(dynamoDb.updateItem(any(UpdateItemRequest.class))).thenReturn(updateResponse);

        APIGatewayProxyResponseEvent result = handler.handleRequest(event, context);

        assertEquals(200, result.getStatusCode());
        ResponseMessage message = objectMapper.readValue(result.getBody(), ResponseMessage.class);
        assertEquals("username successfully changed!", message.getMessage());
    }

    @Test
    public void testHandleRequest_invalidOldUsername() throws Exception {
        String userId = "user123";
        String oldUsername = "wrong_name";
        String newUsername = "new_name";

        UserForm form = new UserForm();
        form.setUser_id(null);
        form.setOldUsername(oldUsername);
        form.setNewUsername(newUsername);

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        Map<String, String> claims = Map.of("sub", "11111111-2222-3333-4444-555555555555");
        Map<String, Object> authorizer = new HashMap<>();
        authorizer.put("claims", claims);

        APIGatewayProxyRequestEvent.ProxyRequestContext rc = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        rc.setAuthorizer(authorizer);
        event.setRequestContext(rc);

        // Path param "s" since client calls /users/s
        event.setPathParameters(Map.of("user_id", "s"));
        event.setBody(objectMapper.writeValueAsString(form));

        Map<String, AttributeValue> userItem = Map.of("username", AttributeValue.fromS("real_name"));
        GetItemResponse getResponse = GetItemResponse.builder().item(userItem).build();
        when(dynamoDb.getItem(any(GetItemRequest.class))).thenReturn(getResponse);

        APIGatewayProxyResponseEvent result = handler.handleRequest(event, context);

        assertEquals(400, result.getStatusCode());
        ResponseMessage message = objectMapper.readValue(result.getBody(), ResponseMessage.class);
        assertEquals("sorry, there was an error processing your request", message.getMessage());
    }

    @Test
    public void testHandleRequest_missingUserId() throws Exception {
        UserForm form = new UserForm(); // no user_id set
        form.setOldUsername("old");
        form.setNewUsername("new");

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        Map<String, String> claims = Map.of("sub", "11111111-2222-3333-4444-555555555555");
        Map<String, Object> authorizer = new HashMap<>();
        //authorizer.put("claims", claims);

        APIGatewayProxyRequestEvent.ProxyRequestContext rc = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        rc.setAuthorizer(authorizer);
        event.setRequestContext(rc);

        // Path param "s" since client calls /users/s
        event.setPathParameters(Map.of("user_id", "s"));
        event.setBody(objectMapper.writeValueAsString(form));

        APIGatewayProxyResponseEvent result = handler.handleRequest(event, context);

        assertEquals(401, result.getStatusCode());
        assertTrue(result.getBody().contains("Unauthorized"));
    }
}
