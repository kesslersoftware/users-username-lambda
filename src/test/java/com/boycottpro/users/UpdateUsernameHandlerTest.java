package com.boycottpro.users;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        form.setUser_id(userId);
        form.setOldUsername(oldUsername);
        form.setNewUsername(newUsername);

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody(objectMapper.writeValueAsString(form));

        Map<String, AttributeValue> userItem = Map.of("username", AttributeValue.fromS(oldUsername));
        GetItemResponse getResponse = GetItemResponse.builder().item(userItem).build();
        when(dynamoDb.getItem(any(GetItemRequest.class))).thenReturn(getResponse);

        UpdateItemResponse updateResponse = UpdateItemResponse.builder().build();
        when(dynamoDb.updateItem(any(UpdateItemRequest.class))).thenReturn(updateResponse);

        APIGatewayProxyResponseEvent result = handler.handleRequest(request, context);

        assertEquals(200, result.getStatusCode());
        assertEquals("username changed = true", result.getBody());
    }

    @Test
    public void testHandleRequest_invalidOldUsername() throws Exception {
        String userId = "user123";
        String oldUsername = "wrong_name";
        String newUsername = "new_name";

        UserForm form = new UserForm();
        form.setUser_id(userId);
        form.setOldUsername(oldUsername);
        form.setNewUsername(newUsername);

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody(objectMapper.writeValueAsString(form));

        Map<String, AttributeValue> userItem = Map.of("username", AttributeValue.fromS("real_name"));
        GetItemResponse getResponse = GetItemResponse.builder().item(userItem).build();
        when(dynamoDb.getItem(any(GetItemRequest.class))).thenReturn(getResponse);

        APIGatewayProxyResponseEvent result = handler.handleRequest(request, context);

        assertEquals(400, result.getStatusCode());
        assertEquals("old username is not valid", result.getBody());
    }

    @Test
    public void testHandleRequest_missingUserId() throws Exception {
        UserForm form = new UserForm(); // no user_id set
        form.setOldUsername("old");
        form.setNewUsername("new");

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody(objectMapper.writeValueAsString(form));

        APIGatewayProxyResponseEvent result = handler.handleRequest(request, context);

        assertEquals(400, result.getStatusCode());
        assertEquals("no user_id is given", result.getBody());
    }
}
