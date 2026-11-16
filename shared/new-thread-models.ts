import { z } from "zod";
import { recipient } from "./recipient";
import { agentProviderSchema, type AgentProvider } from "./agents";
import { windowFor } from "./model-fit";
import { sameAdvisor } from "./advisor";
import { projectChatSendSchema, type ProjectChatSend } from "./projects";

/** The model an agent last ran with; a model of "" is its Default. */
export const newThreadModelSchema = projectChatSendSchema
  .pick({ choice: true, contextWindow: true, advisor: true })
  .strict();
export type NewThreadModel = z.infer<typeof newThreadModelSchema>;

/**
 * Each agent's model a new thread starts on, on the desktop or the phone: the
 * one last picked for a new thread or sent with in any.
 */
export const newThreadModelsSchema = z.partialRecord(
  agentProviderSchema,
  newThreadModelSchema,
);

export type NewThreadModels = z.infer<typeof newThreadModelsSchema>;

/** `model` as a new thread takes it up: an advisor stays with the thread it was picked in. */
export const withoutAdvisor = ({
  advisor: _,
  ...model
}: NewThreadModel): NewThreadModel => model;

/** Every agent's model in `models`, without an advisor. */
export const withoutAdvisors = (models: NewThreadModels): NewThreadModels =>
  Object.fromEntries(
    Object.entries(models).map(([p, m]) => [p, m && withoutAdvisor(m)]),
  );

/** The agent a sent message went to and its model; none when it went to people. */
export function sentModel(
  send: Pick<
    ProjectChatSend,
    "body" | "to" | "provider" | "choice" | "contextWindow"
  >,
): [AgentProvider, NewThreadModel] | undefined {
  if (recipient(send) !== send.provider) return;
  return [
    send.provider,
    {
      choice: send.choice,
      ...windowFor(send.provider, send.choice.model, send.contextWindow),
    },
  ];
}

export const sameModel = (a?: NewThreadModel, b?: NewThreadModel) =>
  a?.choice.model === b?.choice.model &&
  a?.choice.reasoningEffort === b?.choice.reasoningEffort &&
  a?.choice.fast === b?.choice.fast &&
  a?.contextWindow === b?.contextWindow &&
  sameAdvisor(a?.advisor, b?.advisor);
