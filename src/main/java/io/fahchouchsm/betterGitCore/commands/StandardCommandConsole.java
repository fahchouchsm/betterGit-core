package io.fahchouchsm.betterGitCore.commands;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Standard terminal input and output. */
public final class StandardCommandConsole implements CommandConsole {
    private final BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

    @Override
    public String readLine(String prompt) throws IOException {
        System.out.print(prompt);
        String answer = input.readLine();
        return answer == null ? "" : answer;
    }

    @Override
    public void println(String message) {
        System.out.println(message);
    }
}
