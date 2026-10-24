using System.Net.Http;
using System.Net.Http.Json;
using System.Text.Json;

namespace MiniMes.Pop.Api;

/// <summary>서버 REST API를 HttpClient로 부른다.</summary>
public sealed class MesApiClient : IMesApi
{
    // Web 기본값: 필드 이름 camelCase, 대소문자 구분 없이 읽기. 서버(Spring + Jackson) JSON과 맞는다.
    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web);

    private readonly HttpClient _http;

    public MesApiClient(Uri baseAddress, TimeSpan timeout)
    {
        _http = new HttpClient { BaseAddress = baseAddress, Timeout = timeout };
    }

    public Task<Worker> GetWorkerAsync(string workerId) =>
        GetAsync<Worker>($"api/workers/{Uri.EscapeDataString(workerId)}");

    public Task<IReadOnlyList<Equipment>> GetEquipmentAsync() =>
        GetAsync<IReadOnlyList<Equipment>>("api/equipment");

    public Task<IReadOnlyList<CommonCode>> GetCodesAsync(string groupCode) =>
        GetAsync<IReadOnlyList<CommonCode>>($"api/common-codes/{groupCode}");

    public Task<IReadOnlyList<OperationItem>> GetOpenOperationsAsync(string wcCode) =>
        GetAsync<IReadOnlyList<OperationItem>>($"api/work-centers/{wcCode}/operations");

    public Task<OperationState> StartAsync(long woOpId, string workerId) =>
        PostAsync<OperationState>($"api/operations/{woOpId}/start", new { workerId });

    public Task<IReadOnlyList<InputLots>> GetInputLotsAsync(long woOpId) =>
        GetAsync<IReadOnlyList<InputLots>>($"api/operations/{woOpId}/input-lots");

    public Task<WorkResult> ReportAsync(long woOpId, ReportRequest request) =>
        PostAsync<WorkResult>($"api/operations/{woOpId}/results", request);

    public Task<OperationState> CompleteAsync(long woOpId, string workerId) =>
        PostAsync<OperationState>($"api/operations/{woOpId}/complete", new { workerId });

    public Task<IReadOnlyList<WorkResult>> GetResultsAsync(long woOpId) =>
        GetAsync<IReadOnlyList<WorkResult>>($"api/operations/{woOpId}/results");

    public Task<WorkResult> CancelResultAsync(long resultId, string workerId, string reason) =>
        PostAsync<WorkResult>($"api/results/{resultId}/cancel", new { workerId, reason });

    public Task<InspectionSheet> GetInspectionAsync(string lotNo) =>
        GetAsync<InspectionSheet>($"api/lots/{Uri.EscapeDataString(lotNo)}/inspection");

    public Task<InspectionSheet> InspectAsync(string lotNo, InspectRequest request) =>
        PostAsync<InspectionSheet>($"api/lots/{Uri.EscapeDataString(lotNo)}/inspection", request);

    public Task<IReadOnlyList<Downtime>> GetDowntimesAsync(string equipCode, int limit) =>
        GetAsync<IReadOnlyList<Downtime>>($"api/equipment/{Uri.EscapeDataString(equipCode)}/downtimes?limit={limit}");

    public Task<Downtime> ConfirmDowntimeReasonAsync(long downtimeId, ReasonRequest request) =>
        SendAsync<Downtime>(() => _http.PutAsJsonAsync($"api/downtimes/{downtimeId}/reason", request, Json));

    private Task<T> GetAsync<T>(string path) => SendAsync<T>(() => _http.GetAsync(path));

    private Task<T> PostAsync<T>(string path, object body) => SendAsync<T>(() => _http.PostAsJsonAsync(path, body, Json));

    private static async Task<T> SendAsync<T>(Func<Task<HttpResponseMessage>> send)
    {
        HttpResponseMessage response;
        try
        {
            response = await send();
        }
        catch (HttpRequestException e)
        {
            throw new MesConnectionException("서버에 연결할 수 없습니다.", e);
        }
        catch (TaskCanceledException e)   // HttpClient.Timeout이 지나면 TaskCanceledException이 난다
        {
            throw new MesConnectionException("서버 응답이 없습니다.", e);
        }

        using (response)
        {
            if (!response.IsSuccessStatusCode)
            {
                var error = await ReadErrorAsync(response);
                throw new MesApiException((int)response.StatusCode, error.Code, error.Message);
            }
            return (await response.Content.ReadFromJsonAsync<T>(Json))!;
        }
    }

    private static async Task<ApiError> ReadErrorAsync(HttpResponseMessage response)
    {
        try
        {
            return await response.Content.ReadFromJsonAsync<ApiError>(Json)
                   ?? new ApiError("UNKNOWN", $"HTTP {(int)response.StatusCode}");
        }
        catch (JsonException)
        {
            return new ApiError("UNKNOWN", $"HTTP {(int)response.StatusCode}");   // 서버 형식이 아닌 오류 (예: 프록시)
        }
    }
}
