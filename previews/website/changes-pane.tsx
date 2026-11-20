// The panel the Running card's changes row opens: Files on its Changes tab,
// the working tree split into staged and working changes, the commit form,
// and a picked file in the real editor with its change bars. The markup and
// classes are the app's own (features/files, features/changes/local), so
// its CSS draws it; only the data is sample.
import { useEffect, useRef, useState } from "react";
import { EditorState } from "@codemirror/state";
import { EditorView, lineNumbers } from "@codemirror/view";
import {
  ChevronRight,
  ChevronsLeftRight,
  Columns2,
  Files as FilesIcon,
  FolderTree,
  History,
  List,
  PanelLeftClose,
  Split,
  SquareArrowOutUpRight,
  X,
} from "lucide-react";
import { FileEntryIcon } from "../../src/ui/FileEntryIcon";
import { IconButton } from "../../src/ui/ui";
import { PaneTabs } from "../../src/ui/PaneTabs";
import { EditorBar } from "../../src/features/editor/EditorBar";
import { NoFileOpen } from "../../src/features/files/NoFileOpen";
import { loadLanguage } from "../../src/lib/code-languages";
import { editorChrome } from "../../src/features/editor/editor-theme";
import { useEditorThemeNamed } from "../../src/features/editor/useEditorTheme";
import { shadowMount } from "../../src/features/editor/shadow-mount";
import { guides } from "../../src/features/editor/view-aids/guides";
import { inlineChanges } from "../../src/features/editor/useEditorChanges";
import "../../src/ui/workspace-panes.css";
import "../../src/features/panel/panel.css";
import "../../src/ui/middle-truncate.css";
import "../../src/features/changes/working-tree.css";
import "../../src/features/editor/editor.css";
import { useSiteTheme } from "./theme";
import { sampleChanges, type SampleChange } from "./changes-sample";

const nameOf = (path: string) => path.slice(path.lastIndexOf("/") + 1);
const folderOf = (path: string) => path.slice(0, path.lastIndexOf("/"));

const status = {
  loading: false,
  needsFolder: false,
  dirty: false,
  saving: false,
  saved: false,
  changedOnDisk: false,
};

/** A read-only CodeMirror view on the file, with its changes since the last commit. */
function ChangedCode({ file }: { file: SampleChange }) {
  const host = useRef<HTMLDivElement>(null);
  const { resolved } = useSiteTheme();
  const theme = useEditorThemeNamed(resolved.palette.syntax);
  useEffect(() => {
    if (!theme) return;
    let stale = false;
    let view: EditorView | undefined;
    void loadLanguage(file.path).then((language) => {
      const mount = host.current;
      if (stale || !mount) return;
      view = new EditorView({
        parent: shadowMount(mount),
        root: shadowMount(mount),
        state: EditorState.create({
          doc: file.now,
          extensions: [
            lineNumbers(),
            EditorState.readOnly.of(true),
            EditorView.editable.of(false),
            editorChrome,
            theme,
            language ?? [],
            guides,
            inlineChanges(file.committed),
          ],
        }),
      });
    });
    return () => {
      stale = true;
      view?.destroy();
    };
  }, [file, theme]);
  return (
    <div className="local-editor-surface">
      <div className="code-editor" ref={host} />
    </div>
  );
}

function FileRow({
  file,
  index,
  staged,
  selected,
  onStage,
  onOpen,
}: {
  file: SampleChange;
  index: number;
  staged: boolean;
  selected: boolean;
  onStage: () => void;
  onOpen: () => void;
}) {
  const kind = file.added ? "added" : "modified";
  return (
    <div className={`working-file ${selected ? "selected" : ""}`}>
      <input
        type="checkbox"
        aria-label={`${staged ? "Unstage" : "Stage"} ${file.path}`}
        checked={staged}
        data-rw-stage={index}
        onChange={onStage}
      />
      <button className="change-select" data-rw-file={index} onClick={onOpen}>
        <FileEntryIcon path={file.path} directory={false} />
        <span className={`change-name ${kind}`}>
          <span className="middle-truncate">
            <span className="middle-truncate-shown">{nameOf(file.path)}</span>
          </span>
        </span>
        <span className="change-dir">{folderOf(file.path)}</span>
      </button>
    </div>
  );
}

function Section({
  title,
  check,
  count,
  children,
  views,
}: {
  title: string;
  check: string;
  count: number;
  children: React.ReactNode;
  views?: boolean;
}) {
  return (
    <section>
      <header>
        <button
          className="change-section-toggle"
          aria-label={title}
          aria-expanded
        >
          <ChevronRight size={14} />
        </button>
        <input type="checkbox" aria-label={check} checked={false} readOnly />
        <strong>{title}</strong>
        <span className="change-section-count">
          {count} <span>{count === 1 ? "file" : "files"}</span>
        </span>
        {views ? (
          <span
            className="list-view-toggle"
            role="group"
            aria-label="Show files"
          >
            <IconButton label="Group by folder" onClick={() => {}}>
              <FolderTree size={14} />
            </IconButton>
            <IconButton label="Flat list" active onClick={() => {}}>
              <List size={14} />
            </IconButton>
          </span>
        ) : null}
      </header>
      {children}
    </section>
  );
}

export function ChangesPane() {
  const [open, setOpen] = useState<number>();
  const [staged, setStaged] = useState<boolean[]>(() =>
    sampleChanges.map(() => false),
  );
  const file = open === undefined ? undefined : sampleChanges[open];
  const row = (entry: SampleChange, index: number) => (
    <FileRow
      key={entry.path}
      file={entry}
      index={index}
      staged={staged[index]}
      selected={index === open}
      onStage={() =>
        setStaged(staged.map((value, i) => (i === index ? !value : value)))
      }
      onOpen={() => setOpen(index)}
    />
  );
  const stagedCount = staged.filter(Boolean).length;
  return (
    <section className="workspace-pane rw-panel" aria-label="Files">
      <header className="pane-header panel-header">
        <PaneTabs
          tabs={[
            {
              key: "files",
              label: file ? nameOf(file.path) : "Files",
              icon: <FilesIcon size={14} />,
              closeLabel: "Close files",
            },
          ]}
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
      <div className="panel-body">
        <div className="files-workspace">
          <aside className="project-file-list">
            <div className="sidebar-views" role="tablist" aria-label="Sidebar">
              <button role="tab" aria-selected={false}>
                Files
              </button>
              <button role="tab" aria-selected className="active">
                Changes
                <span className="sidebar-views-count">
                  {sampleChanges.length}
                </span>
              </button>
            </div>
            <section
              className="local-changes list-only"
              aria-label="Local changes"
            >
              <div className="working-content">
                <aside className="working-sidebar">
                  <div className="working-file-list">
                    <Section
                      title="Staged changes"
                      check="Unstage all"
                      count={stagedCount}
                      views
                    >
                      {sampleChanges.map((entry, index) =>
                        staged[index] ? row(entry, index) : null,
                      )}
                    </Section>
                    <Section
                      title="Working changes"
                      check="Stage all"
                      count={sampleChanges.length - stagedCount}
                    >
                      {sampleChanges.map((entry, index) =>
                        staged[index] ? null : row(entry, index),
                      )}
                    </Section>
                  </div>
                  <form
                    className="working-commit"
                    onSubmit={(event) => event.preventDefault()}
                  >
                    <textarea
                      aria-label="Commit message"
                      placeholder="Commit message"
                      readOnly
                    />
                    <button className="primary" disabled>
                      Commit staged
                    </button>
                    <button type="button" className="working-split">
                      <Split size={13} />
                      Split into commits…
                    </button>
                  </form>
                </aside>
              </div>
            </section>
          </aside>
          <div className="files-main">
            {file ? (
              <div className="editor-groups row">
                <section className="editor-group">
                  <div className="editor-tabs">
                    <IconButton
                      label="Hide file list"
                      className="file-list-toggle"
                      onClick={() => {}}
                    >
                      <PanelLeftClose size={14} />
                    </IconButton>
                    <div
                      className="editor-tabs-strip"
                      role="tablist"
                      aria-label="Open files"
                    >
                      <div
                        role="tab"
                        aria-selected
                        className="editor-tab active"
                      >
                        <span className="editor-tab-name">
                          {nameOf(file.path)}
                        </span>
                        <span className="editor-tab-close">
                          <X size={12} />
                        </span>
                      </div>
                    </div>
                    <IconButton
                      label="Split right"
                      className="editor-split-button"
                      onClick={() => {}}
                    >
                      <Columns2 size={14} />
                    </IconButton>
                    <IconButton
                      label="Open editor in new window"
                      className="editor-window-button"
                      onClick={() => {}}
                    >
                      <SquareArrowOutUpRight size={14} />
                    </IconButton>
                  </div>
                  <div className="files-main">
                    <section
                      className="project-inline-editor"
                      aria-label="Code editor"
                    >
                      <ChangedCode key={file.path} file={file} />
                      <EditorBar
                        path={file.path}
                        status={status}
                        servers={<span className="lsp-status">TypeScript</span>}
                        actions={
                          <IconButton label="Local history" onClick={() => {}}>
                            <History size={14} />
                          </IconButton>
                        }
                        base="HEAD"
                        compare
                        onCompare={() => {}}
                        saveKeys=""
                        onSave={() => {}}
                        onReload={() => {}}
                      />
                    </section>
                  </div>
                </section>
              </div>
            ) : (
              <NoFileOpen onFind={() => {}} onFindInFiles={() => {}} />
            )}
          </div>
        </div>
      </div>
    </section>
  );
}
