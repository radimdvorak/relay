// The Files panel with the code editor: the app's panel header, editor tabs
// and status line around a real CodeMirror view on a sample file. A pointer
// click on the call opens its docs, one on the usages count over its
// declaration lists them, and one on the wrong argument shows the language
// server's error.
import { useEffect, useRef, useState } from "react";
import type { EditorState } from "@codemirror/state";
import {
  ChevronsLeftRight,
  Columns2,
  Files as FilesIcon,
  History,
  PanelLeftOpen,
  SquareArrowOutUpRight,
  X,
} from "lucide-react";
import { IconButton } from "../../src/ui/ui";
import { PaneTabs } from "../../src/ui/PaneTabs";
import { EditorBar } from "../../src/features/editor/EditorBar";
import { mountHoverCard } from "../../src/features/editor/hover-card/HoverCard";
import "../../src/features/editor/editor.css";
import "../../src/features/editor/symbol-picker.css";
import { errorText, hover, path, usages } from "./editor-sample";
import { useSampleEditor, type Spot } from "./sample-editor";

type Popup = "docs" | "usages" | "error";

const status = {
  loading: false,
  needsFolder: false,
  dirty: false,
  saving: false,
  saved: false,
  changedOnDisk: false,
};

/** A card below the word when it fits there (`room` px), else above it. */
function cardNear(spot: Spot, frameHeight: number, room: number, left = spot.left) {
  const below = spot.top + spot.height + 4;
  if (below + room <= frameHeight) return { left, top: below };
  return { left, bottom: frameHeight - spot.top + 4 };
}

const place = (spot: Spot) => ({
  left: spot.left,
  top: spot.top,
  width: spot.width,
  height: spot.height,
});

/** The hover card the editor draws for the call, mounted from the app's own component. */
function DocsCard({ state }: { state: EditorState }) {
  const slot = useRef<HTMLDivElement>(null);
  useEffect(() => {
    // The card renders its own React root, which can't start or stop during this render.
    let card: ReturnType<typeof mountHoverCard> | undefined;
    const mounted = setTimeout(() => {
      card = mountHoverCard(state, hover);
      slot.current?.append(card.dom);
    });
    return () => {
      clearTimeout(mounted);
      setTimeout(() => card?.destroy());
    };
  }, [state]);
  return <div ref={slot} />;
}

function UsagesList() {
  return (
    <div className="symbol-picker" role="dialog" aria-label="Usages">
      <div className="symbol-picker-head">
        <strong>Usages</strong>
        <span>{usages.length} places</span>
      </div>
      <input aria-label="Filter usages" placeholder="Type to filter…" readOnly />
      <div className="symbol-picker-list">
        {usages.map((usage, index) => {
          const folder = usage.path.slice(0, usage.path.lastIndexOf("/"));
          const first = usage.path !== usages[index - 1]?.path;
          return (
            <div key={usage.line + usage.path}>
              {first ? (
                <div className="symbol-picker-file">
                  {usage.path.split("/").pop()}
                  <span>{folder}</span>
                </div>
              ) : null}
              <div className="symbol-picker-row" aria-selected={index === 1}>
                <span className="symbol-picker-line">{usage.line}</span>
                <code>{usage.preview}</code>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

export function EditorPane() {
  const frame = useRef<HTMLDivElement>(null);
  const host = useRef<HTMLDivElement>(null);
  const spots = useSampleEditor(host, frame);
  const [popup, setPopup] = useState<Popup>();
  const choose = (next: Popup) => setPopup(popup === next ? undefined : next);
  return (
    <section className="workspace-pane rw-panel rw-editor" aria-label="Files">
      <header className="pane-header panel-header">
        <PaneTabs
          tabs={[{ key: "files", label: "queue.ts", icon: <FilesIcon size={14} /> }]}
          front="files"
          onFront={() => {}}
          onClose={() => {}}
          add={{ label: "Open another surface", onClick: () => {} }}
        />
        <IconButton label="Expand panel" onClick={() => {}}>
          <ChevronsLeftRight size={15} />
        </IconButton>
        <IconButton label="Close panel" onClick={() => {}}>
          <X size={15} />
        </IconButton>
      </header>
      <div className="editor-tabs">
        <IconButton label="Show file list" className="file-list-toggle" onClick={() => {}}>
          <PanelLeftOpen size={14} />
        </IconButton>
        <div className="editor-tabs-strip" role="tablist" aria-label="Open files">
          <div role="tab" aria-selected className="editor-tab active">
            <span className="editor-tab-name">queue.ts</span>
            <span className="editor-tab-close">
              <X size={12} />
            </span>
          </div>
        </div>
        <IconButton label="Split right" className="editor-split-button" onClick={() => {}}>
          <Columns2 size={14} />
        </IconButton>
        <IconButton label="Open editor in new window" className="editor-window-button" onClick={() => {}}>
          <SquareArrowOutUpRight size={14} />
        </IconButton>
      </div>
      <section className="project-inline-editor" aria-label="Code editor">
      <div className="rw-editor-code" ref={frame}>
        <div className="local-editor-surface">
          <div className="code-editor" ref={host} />
        </div>
        {spots ? (
          <>
            <button
              type="button"
              className="rw-spot"
              aria-label="reorder"
              data-rw-symbol
              style={place(spots.symbol)}
              onClick={() => choose("docs")}
            />
            {spots.usages ? (
              <button
                type="button"
                className="rw-spot"
                aria-label="Usages of reorder"
                data-rw-usages
                style={place(spots.usages)}
                onClick={() => choose("usages")}
              />
            ) : null}
            <button
              type="button"
              className="rw-spot"
              aria-label="Wrong argument"
              data-rw-error
              style={place(spots.mistake)}
              onClick={() => choose("error")}
            />
            {popup === "docs" ? (
              <div className="rw-tip" style={cardNear(spots.symbol, spots.frameHeight, 230)}>
                <DocsCard state={spots.view.state} />
              </div>
            ) : null}
            {popup === "error" ? (
              <div className="rw-tip rw-tip-error" style={cardNear(spots.mistake, spots.frameHeight, 90, spots.mistake.left - 80)}>
                {errorText}
              </div>
            ) : null}
          </>
        ) : null}
        {popup === "usages" ? (
          <div className="rw-usages">
            <UsagesList />
          </div>
        ) : null}
      </div>
      <EditorBar
        path={path}
        status={status}
        servers={<span className="lsp-status">vtsls</span>}
        checks={
          <div className="editor-checks">
            <span>1 error · 0 warnings</span>
          </div>
        }
        actions={
          <IconButton label="Local history" onClick={() => {}}>
            <History size={14} />
          </IconButton>
        }
        base="HEAD"
        compare={false}
        onCompare={() => {}}
        saveKeys=""
        onSave={() => {}}
        onReload={() => {}}
      />
      </section>
    </section>
  );
}
