package com.boycottpro.users;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import com.boycottpro.models.ResponseMessage;
import com.boycottpro.users.models.UserForm;
import com.boycottpro.utilities.JwtUtility;
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
            String sub = JwtUtility.getSubFromRestEvent(event);
            if (sub == null) return response(401, "Unauthorized");
            UserForm input = objectMapper.readValue(event.getBody(), UserForm.class);
            input.setUser_id(sub);
            if(!checkOldUsername(sub, input.getOldUsername())) {
                ResponseMessage message = new ResponseMessage(400,
                        "sorry, there was an error processing your request",
                        "old username is not valid");
                String responseBody = objectMapper.writeValueAsString(message);
                return response(400, responseBody);
            }
            boolean success = changeUsername(sub,input.getNewUsername());
            if(success) {
                ResponseMessage message = new ResponseMessage(200,"username successfully changed!",
                        "no issues changing username");
                String responseBody = objectMapper.writeValueAsString(message);
                return response(200, responseBody);
            } else {
                ResponseMessage message = new ResponseMessage(500,
                        "sorry, there was an error processing your request",
                        "unknown issue when trying to change username");
                String responseBody = objectMapper.writeValueAsString(message);
                return response(500, responseBody);
            }

        } catch (Exception e) {
            ResponseMessage message = new ResponseMessage(500,
                    "sorry, there was an error processing your request",
                    "Unexpected server error: " + e.getMessage());
            String responseBody = null;
            try {
                responseBody = objectMapper.writeValueAsString(message);
            } catch (JsonProcessingException ex) {
                throw new RuntimeException(ex);
            }
            return response(500, responseBody);
        }
    }

    private APIGatewayProxyResponseEvent response(int status, String body) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(status)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(body);
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