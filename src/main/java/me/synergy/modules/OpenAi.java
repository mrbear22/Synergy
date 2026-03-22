package me.synergy.modules;

import java.time.Duration;
import java.util.List;

import com.theokanning.openai.completion.CompletionChoice;
import com.theokanning.openai.completion.CompletionRequest;
import com.theokanning.openai.service.OpenAiService;

import me.synergy.brains.Synergy;

public class OpenAi {
  public List<CompletionChoice> newPrompt(String args) {
    OpenAiService service = new OpenAiService(Config.getString("openai.token"), Duration.ofSeconds(30L));
    CompletionRequest completionRequest = CompletionRequest.builder()
      .model(Config.getString("openai.model"))
      .prompt(args)
      .maxTokens(Config.getInt("openai.response-size"))
      .temperature(Double.valueOf(Config.getDouble("openai.temperature")))
      .build();
    List<CompletionChoice> choices = service.createCompletion(completionRequest).getChoices();
    service.shutdownExecutor();
    return choices;
  }
}
