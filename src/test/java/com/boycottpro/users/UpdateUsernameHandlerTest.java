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
import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.core.JsonProcessingException;

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

    @Test
    public void testDefaultConstructor() {
        // Test the default constructor coverage
        // Note: This may fail in environments without AWS credentials/region configured
        try {
            UpdateUsernameHandler handler = new UpdateUsernameHandler();
            assertNotNull(handler);

            // Verify DynamoDbClient was created (using reflection to access private field)
            try {
                Field dynamoDbField = UpdateUsernameHandler.class.getDeclaredField("dynamoDb");
                dynamoDbField.setAccessible(true);
                DynamoDbClient dynamoDb = (DynamoDbClient) dynamoDbField.get(handler);
                assertNotNull(dynamoDb);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                fail("Failed to access DynamoDbClient field: " + e.getMessage());
            }
        } catch (software.amazon.awssdk.core.exception.SdkClientException e) {
            // AWS SDK can't initialize due to missing region configuration
            // This is expected in Jenkins without AWS credentials - test passes
            System.out.println("Skipping DynamoDbClient verification due to AWS SDK configuration: " + e.getMessage());
        }
    }

    @Test
    public void testUnauthorizedUser() {
        // Test the unauthorized block coverage
        handler = new UpdateUsernameHandler(dynamoDb);

        // Create event without JWT token (or invalid token that returns null sub)
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        // No authorizer context, so JwtUtility.getSubFromRestEvent will return null

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, null);

        assertEquals(401, response.getStatusCode());
        assertTrue(response.getBody().contains("Unauthorized"));
    }

    @Test
    public void testJsonProcessingExceptionInResponse() throws Exception {
        // Test JsonProcessingException coverage in response method by using reflection
        handler = new UpdateUsernameHandler(dynamoDb);

        // Use reflection to access the private response method
        java.lang.reflect.Method responseMethod = UpdateUsernameHandler.class.getDeclaredMethod("response", int.class, Object.class);
        responseMethod.setAccessible(true);

        // Create an object that will cause JsonProcessingException
        Object problematicObject = new Object() {
            public Object writeReplace() throws java.io.ObjectStreamException {
                throw new java.io.NotSerializableException("Not serializable");
            }
        };

        // Create a circular reference object that will cause JsonProcessingException
        Map<String, Object> circularMap = new HashMap<>();
        circularMap.put("self", circularMap);

        // This should trigger the JsonProcessingException -> RuntimeException path
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            try {
                responseMethod.invoke(handler, 500, circularMap);
            } catch (java.lang.reflect.InvocationTargetException e) {
                if (e.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) e.getCause();
                }
                throw new RuntimeException(e.getCause());
            }
        });

        // Verify it's ultimately caused by JsonProcessingException
        Throwable cause = exception.getCause();
        assertTrue(cause instanceof JsonProcessingException,
                "Expected JsonProcessingException, got: " + cause.getClass().getSimpleName());
    }

    @Test
    public void testGenericExceptionHandling() {
        // Test the generic Exception catch block coverage
        handler = new UpdateUsernameHandler(dynamoDb);

        // Create a valid JWT event
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        Map<String, String> claims = Map.of("sub", "11111111-2222-3333-4444-555555555555");
        Map<String, Object> authorizer = new HashMap<>();
        authorizer.put("claims", claims);

        APIGatewayProxyRequestEvent.ProxyRequestContext rc = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        rc.setAuthorizer(authorizer);
        event.setRequestContext(rc);

        // Mock DynamoDB to throw a generic exception (e.g., RuntimeException)
        //when(dynamoDb.updateItem(any(UpdateItemRequest.class))).thenThrow(new RuntimeException("Database connection failed"));

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(event, null);

        // Assert
        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("Unexpected server error"));
    }

    @Test
    public void testHandleRequest_userNotFound() throws Exception {
        // Test lines 102-103: when user doesn't exist in database (response.hasItem() returns false)
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
        event.setPathParameters(Map.of("user_id", "s"));
        event.setBody(objectMapper.writeValueAsString(form));

        // Mock GetItemResponse with no item (user not found)
        GetItemResponse getResponse = GetItemResponse.builder().build(); // No item set
        when(dynamoDb.getItem(any(GetItemRequest.class))).thenReturn(getResponse);

        APIGatewayProxyResponseEvent result = handler.handleRequest(event, context);

        // Should return 400 because checkOldUsername returns false (user not found)
        assertEquals(400, result.getStatusCode());
        ResponseMessage message = objectMapper.readValue(result.getBody(), ResponseMessage.class);
        assertEquals("sorry, there was an error processing your request", message.getMessage());
        assertTrue(message.getDevMsg().contains("old username is not valid"));
    }
}
