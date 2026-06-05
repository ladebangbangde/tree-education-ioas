package com.treeeducation.ioas.dataops;

public class DataOperationAccountWorkflowDtos {
    public record ConfirmAccountRequest(String accountName, String platformUserId, Long operatorUserId) {}
}
