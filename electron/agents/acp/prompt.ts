// What a turn sends an ACP agent: its prompt as content blocks, and Relay's
// tools as an MCP server.
import { readFile } from "node:fs/promises";
import type { AgentOptions } from "../types";
import type { AcpProfile } from "./profiles";
import type { AcpAgent, McpServer } from "./sessions";

/** The turn's prompt as ACP content blocks. */
export async function promptBlocks(
  options: AgentOptions,
  agent: AcpAgent,
  name: string,
  profile: AcpProfile,
) {
  const { job } = options;
  const helper = job.kind === "helper" ? job : undefined;
  const note =
    helper || job.kind === "compact"
      ? undefined
      : await options.context?.().catch(() => undefined);
  const text = job.kind === "compact" ? profile.compact! : options.prompt;
  const body = [
    helper &&
      "This is a text-only helper request. Do not use tools, ask questions, or invoke slash commands.",
    helper?.instructions,
    note,
    text,
  ]
    .filter(Boolean)
    .join("\n\n");
  const planning =
    job.kind === "prompt" &&
    options.interactionMode === "plan" &&
    agent.profile.plan;
  const skills =
    job.kind === "prompt"
      ? ((await agent.profile.skillContext?.(options)) ?? [])
      : [];
  const blocks: object[] = [
    ...skills.map((text) => ({
      type: "text",
      text,
      annotations: { audience: ["assistant"] },
    })),
    { type: "text", text: planning ? `${agent.profile.plan} ${body}` : body },
  ];
  blocks.push(...(await imageBlocks(options.images, agent, name)));
  blocks.push(...(await audioBlocks(options.audio, agent, name, profile)));
  return blocks;
}

/** Audio clips as ACP audio blocks, or a `read_file` note where the agent reads audio from disk. */
async function audioBlocks(
  audio: AgentOptions["audio"],
  agent: AcpAgent,
  name: string,
  profile: AcpProfile,
): Promise<object[]> {
  if (!audio?.length) return [];
  if (agent.caps?.audio)
    return Promise.all(
      audio.map(async (clip) => ({
        type: "audio",
        mimeType: clip.mimeType,
        data: (await readFile(clip.path)).toString("base64"),
      })),
    );
  if (!profile.readsAudioFiles)
    throw new Error(`${name} can't listen to audio.`);
  const files = audio
    .map((clip) => `- ${clip.path} (${clip.mimeType})`)
    .join("\n");
  return [
    {
      type: "text",
      text: `The user attached audio to this message. Call read_file on each path below to listen before answering:\n${files}`,
      annotations: { audience: ["assistant"] },
    },
  ];
}

/** Pictures as content blocks, for a turn's prompt or a steer. */
export async function imageBlocks(
  images: AgentOptions["images"],
  agent: AcpAgent,
  name: string,
): Promise<object[]> {
  if (!images?.length) return [];
  if (!agent.caps?.image) throw new Error(`${name} can't read images.`);
  return Promise.all(
    images.map(async (image) => ({
      type: "image",
      mimeType: image.mimeType,
      data: (await readFile(image.path)).toString("base64"),
    })),
  );
}

/** Relay's own tools, where the agent takes MCP servers over HTTP. */
export const mcpServers = (
  options: AgentOptions,
  agent: AcpAgent,
): McpServer[] =>
  options.relayTools && agent.caps?.http
    ? [
        {
          type: "http",
          name: "relay",
          url: options.relayTools.url,
          headers: [
            {
              name: "Authorization",
              value: `Bearer ${options.relayTools.token}`,
            },
          ],
        },
      ]
    : [];
