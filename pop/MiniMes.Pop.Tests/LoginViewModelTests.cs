using MiniMes.Pop.Api;
using MiniMes.Pop.ViewModels;

namespace MiniMes.Pop.Tests;

public class LoginViewModelTests
{
    [Fact(DisplayName = "사번과 비밀번호가 모두 있어야 로그인 버튼이 켜지고, 맞으면 작업자를 넘긴다")]
    public async Task Login()
    {
        Worker? loggedIn = null;
        var vm = new LoginViewModel(new FakeMesApi(), w => loggedIn = w);
        Assert.False(vm.LoginCommand.CanExecute(null));

        vm.WorkerId = " W1001 ";   // 바코드 앞뒤 공백은 지운다
        Assert.False(vm.LoginCommand.CanExecute(null));
        vm.Password = "1234";
        await vm.LoginCommand.ExecuteAsync(null);

        Assert.Equal("김도윤", loggedIn?.WorkerName);
    }

    [Fact(DisplayName = "비밀번호가 틀리면 서버 메시지를 보여 주고 화면을 바꾸지 않는다")]
    public async Task WrongPassword()
    {
        Worker? loggedIn = null;
        var vm = new LoginViewModel(new FakeMesApi(), w => loggedIn = w) { WorkerId = "W1001", Password = "0000" };

        await vm.LoginCommand.ExecuteAsync(null);

        Assert.Null(loggedIn);
        Assert.Equal("사번 또는 비밀번호가 맞지 않습니다.", vm.Message);
    }
}
