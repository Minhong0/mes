using MiniMes.Pop.Api;
using MiniMes.Pop.Storage;

namespace MiniMes.Pop.Tests;

/// <summary>보내지 못한 실적을 단말 파일에 보관한다. 테스트마다 임시 폴더를 쓴다.</summary>
public sealed class FilePendingReportStoreTests : IDisposable
{
    private readonly string _dir = Path.Combine(Path.GetTempPath(), "minimes-pop-test-" + Guid.NewGuid());

    public void Dispose()
    {
        if (Directory.Exists(_dir))
        {
            Directory.Delete(_dir, recursive: true);
        }
    }

    private static SavedReport Sample(string workerId = "W1001") => new(workerId, "WC-100", 11, "WO-1", "CNC 선삭",
        new ReportRequest("req-1", workerId, "CNC01", 5, [new DefectLine("OVERSIZE", 2)], [new LotInput("RM-SCM-001-261017-001", 7)]), new DateTime(2026, 10, 17, 9, 0, 0));

    [Fact(DisplayName = "적은 것을 그대로 읽고(불량 내역, 투입 LOT 포함), 지우면 없다. 작업자마다 따로 보관한다")]
    public void RoundTrip()
    {
        var store = new FilePendingReportStore(_dir);
        store.Save(Sample());
        store.Save(Sample("W2001"));

        var loaded = store.Load("W1001");

        Assert.NotNull(loaded);
        Assert.Equal("req-1", loaded.Request.RequestId);
        Assert.Equal([new DefectLine("OVERSIZE", 2)], loaded.Request.Defects);
        Assert.Equal([new LotInput("RM-SCM-001-261017-001", 7)], loaded.Request.Inputs);
        Assert.Equal(11, loaded.WoOpId);

        store.Clear("W1001");

        Assert.Null(store.Load("W1001"));
        Assert.NotNull(store.Load("W2001"));
        store.Clear("W1001");   // 없는 것을 지워도 괜찮다
    }

    [Fact(DisplayName = "읽을 수 없는 파일은 지우지 않고 .broken으로 옮겨 둔다")]
    public void BrokenFile()
    {
        Directory.CreateDirectory(_dir);
        File.WriteAllText(Path.Combine(_dir, "W1001.json"), "{ 반쯤 쓴 파일");
        var store = new FilePendingReportStore(_dir);

        Assert.Null(store.Load("W1001"));
        Assert.True(File.Exists(Path.Combine(_dir, "W1001.json.broken")));
    }

    [Fact(DisplayName = "사번에 경로 문자가 있으면 거부한다")]
    public void RejectsPath()
    {
        var store = new FilePendingReportStore(_dir);

        Assert.Throws<ArgumentException>(() => store.Load("..\\other"));
        Assert.Throws<ArgumentException>(() => store.Save(Sample("a/b")));
    }
}
