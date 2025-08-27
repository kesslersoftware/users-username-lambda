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
        String sub = null;
        try {
            sub = JwtUtility.getSubFromRestEvent(event);
            if (sub == null) return response(401, "Unauthorized");
            UserForm input = objectMapper.readValue(event.getBody(), UserForm.class);
            input.setUser_id(sub);
            if(!checkOldUsername(sub, input.getOldUsername())) {
                ResponseMessage message = new ResponseMessage(400,
                        "sorry, there was an error processing your request",
                        "old username is not valid");
                return response(400, message);
            }
            boolean success = changeUsername(sub,input.getNewUsername());
            if(success) {
                ResponseMessage message = new ResponseMessage(200,"username successfully changed!",
                        "no issues changing username");
                return response(200, message);
            } else {
                System.out.println("unknown issue when trying to change username for user " + sub);
                ResponseMessage message = new ResponseMessage(500,
                        "sorry, there was an error processing your request",
                        "unknown issue when trying to change username");
                return response(500, message);
            }

        } catch (Exception e) {
            System.out.println(e.getMessage() + " for user " + sub);
            ResponseMessage message = new ResponseMessage(500,
                    "sorry, there was an error processing your request",
                    "Unexpected server error: " + e.getMessage());
            return response(500, message);
        }
    }

    private APIGatewayProxyResponseEvent response(int status, Object body) {
        String responseBody = null;
        try {
            responseBody = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(status)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(responseBody);
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