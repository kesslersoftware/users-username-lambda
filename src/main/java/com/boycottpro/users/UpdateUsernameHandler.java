package com.boycottpro.users;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import com.boycottpro.users.models.UserForm;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;
import java.util.stream.Collectors;

public class UpdateUsernameHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String TABLE_NAME = "";
    private final DynamoDbClient dynamoDb;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public UpdateUsernameHandler() {
        this.dynamoDb = DynamoDbClient.create();
    }

    public UpdateUsernameHandler(DynamoDbClient dynamoDb) {
        this.dynamoDb = dynamoDb;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            UserForm input = objectMapper.readValue(event.getBody(), UserForm.class);
            String userId = input.getUser_id();
            if (userId == null || userId.isEmpty()) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(Map.of("Content-Type", "application/json"))
                        .withBody("no user_id is given");
            }
            if(!checkOldUsername(userId, input.getOldUsername())) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(Map.of("Content-Type", "application/json"))
                        .withBody("old username is not valid");
            }
            boolean success = changeUsername(userId,input.getNewUsername());
            String responseBody = objectMapper.writeValueAsString(success);
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(Map.of("Content-Type", "application/json"))
                    .withBody("username changed = " + responseBody);
        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\": \"Unexpected server error: " + e.getMessage() + "\"}");
        }
    }

    private boolean changeUsername(String userId, String newUsername) {
        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName("users")
                .key(Map.of("user_id", AttributeValue.fromS(userId)))
                .updateExpression("SET username = :new")
                .expressionAttributeValues(Map.of(":new", AttributeValue.fromS(newUsername)))
                .build();

        dynamoDb.updateItem(request);
        return true;
    }

    private boolean checkOldUsername(String userId, String oldUsername) {
        GetItemRequest request = GetItemRequest.builder()
                .tableName("users")
                .key(Map.of("user_id", AttributeValue.fromS(userId)))
                .projectionExpression("username")
                .build();

        GetItemResponse response = dynamoDb.getItem(request);

        if (!response.hasItem()) {
            return false; // User not found
        }

        String storedUsername = response.item().getOrDefault("username", AttributeValue.fromS("")).s();
        return storedUsername.equals(oldUsername);
    }
}