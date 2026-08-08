package http

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"time"

	"db-fleetops-agent/internal/domain"
	"db-fleetops-agent/internal/port"
)

type ControlPlaneClient struct {
	baseURL    string
	httpClient *http.Client
	agentID    int64
	agentToken string
}

func NewControlPlaneClient(
	baseURL string,
) *ControlPlaneClient {
	return &ControlPlaneClient{
		baseURL: baseURL,
		httpClient: &http.Client{
			Timeout: 5 * time.Second,
		},
	}
}

type registerAgentRequest struct {
	AgentName    string `json:"agentName"`
	Hostname     string `json:"hostname"`
	IPAddress    string `json:"ipAddress"`
	OSName       string `json:"osName"`
	Architecture string `json:"architecture"`
	AgentVersion string `json:"agentVersion"`
}

type registerAgentResponse struct {
	AgentID    int64  `json:"agentId"`
	AgentToken string `json:"agentToken"`
	Status     string `json:"status"`
}

func (c *ControlPlaneClient) RegisterAgent(
	ctx context.Context,
	agentInfo domain.AgentInfo,
) (port.RegistrationResult, error) {
	requestBody := registerAgentRequest{
		AgentName:    agentInfo.AgentName,
		Hostname:     agentInfo.Hostname,
		IPAddress:    agentInfo.IPAddress,
		OSName:       agentInfo.OSName,
		Architecture: agentInfo.Architecture,
		AgentVersion: agentInfo.AgentVersion,
	}

	var responseBody registerAgentResponse

	err := c.postJSON(
		ctx,
		"/internal/v1/agents/register",
		requestBody,
		&responseBody,
	)

	if err != nil {
		return port.RegistrationResult{}, err
	}

	c.agentID = responseBody.AgentID
	c.agentToken = responseBody.AgentToken

	return port.RegistrationResult{
		AgentID:    responseBody.AgentID,
		AgentToken: responseBody.AgentToken,
		Status:     responseBody.Status,
	}, nil
}

type heartbeatRequest struct {
	AgentToken         string  `json:"agentToken"`
	CPUUsagePercent    float64 `json:"cpuUsagePercent"`
	MemoryUsagePercent float64 `json:"memoryUsagePercent"`
	DiskUsagePercent   float64 `json:"diskUsagePercent"`
}

func (c *ControlPlaneClient) SendHeartbeat(
	ctx context.Context,
	agentInfo domain.AgentInfo,
) error {
	if c.agentID == 0 || c.agentToken == "" {
		return fmt.Errorf("agent is not registered")
	}

	requestBody := heartbeatRequest{
		AgentToken:         c.agentToken,
		CPUUsagePercent:    0.0,
		MemoryUsagePercent: 0.0,
		DiskUsagePercent:   0.0,
	}

	path := fmt.Sprintf(
		"/internal/v1/agents/%d/heartbeats",
		c.agentID,
	)

	var responseBody map[string]any

	return c.postJSON(
		ctx,
		path,
		requestBody,
		&responseBody,
	)
}

func (c *ControlPlaneClient) postJSON(
	ctx context.Context,
	path string,
	requestBody any,
	responseBody any,
) error {
	bodyBytes, err := json.Marshal(requestBody)

	if err != nil {
		return err
	}

	request, err := http.NewRequestWithContext(
		ctx,
		http.MethodPost,
		c.baseURL+path,
		bytes.NewReader(bodyBytes),
	)

	if err != nil {
		return err
	}

	request.Header.Set(
		"Content-Type",
		"application/json",
	)

	response, err := c.httpClient.Do(request)

	if err != nil {
		return err
	}

	defer response.Body.Close()

	if response.StatusCode < 200 || response.StatusCode >= 300 {
		if response.StatusCode == http.StatusConflict {
			return port.ErrTaskExecutionConflict
		}
		return fmt.Errorf(
			"control plane returned non-2xx status: %d",
			response.StatusCode,
		)
	}

	if responseBody == nil {
		return nil
	}

	return json.NewDecoder(response.Body).Decode(responseBody)
}

type nextTaskResponse struct {
	HasTask          bool   `json:"hasTask"`
	TaskID           int64  `json:"taskId"`
	TaskType         string `json:"taskType"`
	ParametersJSON   string `json:"parametersJson"`
	ExecutionAttempt int    `json:"executionAttempt"`
	CredentialID     int64  `json:"credentialId"`
}

func (c *ControlPlaneClient) FetchNextTask(
	ctx context.Context,
) (*port.Task, error) {
	if c.agentID == 0 || c.agentToken == "" {
		return nil, fmt.Errorf("agent is not registered")
	}

	path := fmt.Sprintf(
		"/internal/v1/agents/%d/tasks/next?agentToken=%s",
		c.agentID,
		c.agentToken,
	)

	var responseBody nextTaskResponse

	err := c.postJSON(
		ctx,
		path,
		struct{}{},
		&responseBody,
	)

	if err != nil {
		return nil, err
	}

	if !responseBody.HasTask {
		return nil, nil
	}

	return &port.Task{
		TaskID:           responseBody.TaskID,
		TaskType:         responseBody.TaskType,
		ParametersJSON:   responseBody.ParametersJSON,
		ExecutionAttempt: responseBody.ExecutionAttempt,
		CredentialID:     responseBody.CredentialID,
	}, nil
}

type taskCredentialResponse struct {
	Username string `json:"username"`
	Password string `json:"password"`
}

func (c *ControlPlaneClient) ResolveTaskCredential(ctx context.Context, taskID int64,
	executionAttempt int) (port.TaskCredential, error) {
	path := fmt.Sprintf("/internal/v1/agents/%d/tasks/%d/credential", c.agentID, taskID)
	request := renewTaskLeaseRequest{AgentToken: c.agentToken, ExecutionAttempt: executionAttempt}
	var response taskCredentialResponse
	if err := c.postJSON(ctx, path, request, &response); err != nil {
		return port.TaskCredential{}, err
	}
	return port.TaskCredential{Username: response.Username, Password: response.Password}, nil
}

type taskTokenRequest struct {
	AgentToken string `json:"agentToken"`
}

type renewTaskLeaseRequest struct {
	AgentToken       string `json:"agentToken"`
	ExecutionAttempt int    `json:"executionAttempt"`
}

func (c *ControlPlaneClient) RenewTaskLease(
	ctx context.Context,
	taskID int64,
	executionAttempt int,
) error {
	path := fmt.Sprintf(
		"/internal/v1/agents/%d/tasks/%d/lease",
		c.agentID,
		taskID,
	)

	requestBody := renewTaskLeaseRequest{
		AgentToken:       c.agentToken,
		ExecutionAttempt: executionAttempt,
	}

	var responseBody map[string]any

	return c.postJSON(
		ctx,
		path,
		requestBody,
		&responseBody,
	)
}

type completeTaskRequest struct {
	AgentToken        string `json:"agentToken"`
	ResultPayloadJSON string `json:"resultPayloadJson"`
	ExecutionAttempt  int    `json:"executionAttempt"`
	ResultReportID    string `json:"resultReportId"`
}

func (c *ControlPlaneClient) CompleteTask(
	ctx context.Context,
	taskID int64,
	executionAttempt int,
	resultReportID string,
	resultPayloadJSON string,
) error {
	path := fmt.Sprintf(
		"/internal/v1/agents/%d/tasks/%d/complete",
		c.agentID,
		taskID,
	)

	requestBody := completeTaskRequest{
		AgentToken:        c.agentToken,
		ResultPayloadJSON: resultPayloadJSON,
		ExecutionAttempt:  executionAttempt,
		ResultReportID:    resultReportID,
	}

	var responseBody map[string]any

	return c.postJSON(
		ctx,
		path,
		requestBody,
		&responseBody,
	)
}

type failTaskRequest struct {
	AgentToken       string `json:"agentToken"`
	ErrorCode        string `json:"errorCode"`
	ErrorMessage     string `json:"errorMessage"`
	ExecutionAttempt int    `json:"executionAttempt"`
	ResultReportID   string `json:"resultReportId"`
}

func (c *ControlPlaneClient) FailTask(
	ctx context.Context,
	taskID int64,
	executionAttempt int,
	resultReportID string,
	errorCode string,
	errorMessage string,
) error {
	path := fmt.Sprintf(
		"/internal/v1/agents/%d/tasks/%d/fail",
		c.agentID,
		taskID,
	)

	requestBody := failTaskRequest{
		AgentToken:       c.agentToken,
		ErrorCode:        errorCode,
		ErrorMessage:     errorMessage,
		ExecutionAttempt: executionAttempt,
		ResultReportID:   resultReportID,
	}

	var responseBody map[string]any

	return c.postJSON(
		ctx,
		path,
		requestBody,
		&responseBody,
	)
}

func (c *ControlPlaneClient) getJSON(
	ctx context.Context,
	path string,
	responseBody any,
) error {
	request, err := http.NewRequestWithContext(
		ctx,
		http.MethodGet,
		c.baseURL+path,
		nil,
	)

	if err != nil {
		return err
	}

	response, err := c.httpClient.Do(request)

	if err != nil {
		return err
	}

	defer response.Body.Close()

	if response.StatusCode < 200 || response.StatusCode >= 300 {
		return fmt.Errorf(
			"control plane returned non-2xx status: %d",
			response.StatusCode,
		)
	}

	return json.NewDecoder(response.Body).Decode(responseBody)
}

func (c *ControlPlaneClient) SetAgentIdentity(
	agentID int64,
	agentToken string,
) {
	c.agentID = agentID
	c.agentToken = agentToken
}
