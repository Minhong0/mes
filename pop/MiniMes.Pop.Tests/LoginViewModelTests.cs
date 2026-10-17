using MiniMes.Pop.Api;
using MiniMes.Pop.ViewModels;

namespace MiniMes.Pop.Tests;

public class LoginViewModelTests
{
    [Fact(DisplayName = "사번이 비어 있으면 로그인 버튼이 꺼져 있고, 맞는 사번이면 작업자를 넘긴다")]
    public async Task Login()
    {
        Worker? loggedIn = null;
        var vm = new LoginViewModel(new FakeMesApi(), w => loggedIn = w);
        Assert.False(vm.LoginCommand.CanExecute(null));

        vm.WorkerId = " W1001 ";   // 바코드 앞뒤 공백은 지운다
        await vm.LoginCommand.ExecuteAsync(null);

        Assert.Equal("김도윤", loggedIn?.WorkerName);
    }

    [Fact(DisplayName = "없는 사번이면 서버 메시지를 보여 주고 화면을 바꾸지 않는다")]
    public async Task UnknownWorker()
    {
        Worker? loggedIn = null;
        var vm = new LoginViewModel(new FakeMesApi(), w => loggedIn = w) { WorkerId = "W0000" };

        await vm.LoginCommand.ExecuteAsync(null);

        Assert.Null(loggedIn);
        Assert.Equal("작업자를 찾을 수 없습니다: W0000", vm.Message);
    }
}
