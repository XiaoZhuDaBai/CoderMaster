package xiaozhu.judge.codesandbox;


import xiaozhu.judge.model.ExecuteCodeRequest;
import xiaozhu.judge.model.ExecuteCodeResponse;

public interface CodeSandbox {
    ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest);
    ExecuteCodeResponse userTestCode(ExecuteCodeRequest executeCodeRequest);
}
