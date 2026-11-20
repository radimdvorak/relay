// Demo tiles about threads that start or run away from this computer:
// schedules, and the threads on your other computers.
import { ArrowUp, Bug, Clock, Monitor, ScrollText, Server } from "lucide-react";
import { Cursor, Cycle, PLACEHOLDER, Placeholder, Type, on, within } from "./kit";
import type { Loop } from "./kit";

const is = (v: string | undefined) => v !== undefined;

/* ---- Run a prompt on a schedule ---- */
// A template from the gallery (ScheduleGallery.tsx) becomes a card in the
// Schedules list (ScheduleList.tsx); when it's due, its run is a normal
// thread in Activity.
export const scheduleLoop: Loop = {
  times: [0, 900, 1700, 2300, 3800, 4300, 6400, 8400],
  still: 6,
};
const templates = [
  { Icon: Bug, title: "Find critical bugs", blurb: "Read yesterday's commits for high-severity bugs.", when: "Weekdays at 9:00" },
  { Icon: ScrollText, title: "Draft the changelog", blurb: "Turn the week's merged work into release notes.", when: "Fridays at 16:00 · Read only" },
];
export function ScheduleTile({ s }: { s: number }) {
  const made = s >= 3;
  return (
    <div className="sc-tile">
      <div className="sc-gallery" data-gone={made ? "" : undefined}>
        <b className="sc-head">New schedule</b>
        {templates.map(({ Icon, title, blurb, when }, i) => (
          <div className="sc-template" data-hover={i === 1 ? within(s, 1, 3) : undefined} key={title}>
            <span className="sc-icon">
              <Icon size={14} />
            </span>
            <strong>{title}</strong>
            <span className="sc-blurb">{blurb}</span>
            <span className="sc-when">
              <Clock size={10} /> {when}
            </span>
          </div>
        ))}
      </div>
      <div className="sc-after dt-in" data-on={on(s, 3)}>
        <div className="sc-label">
          <span>
            Schedules <small>(1)</small>
          </span>
          <span className="sc-clock">
            <Cycle at={s >= 4 ? 1 : 0} items={["Wed 11:20", "Fri 16:00"]} />
          </span>
        </div>
        <div className="dt-card sc-card">
          <div className="dt-card-top">
            <span className="dt-badge">R</span>
            <span className="dt-card-project">Relay</span>
            <span className="dt-card-state" data-kind={within(s, 4, 6) ? "working" : undefined}>
              <Cycle at={s >= 6 ? 2 : s >= 4 ? 1 : 0} items={["in 2 days", "Running", "in 7 days"]} />
            </span>
          </div>
          <span className="dt-card-title">Draft the changelog</span>
          <div className="dt-card-meta">Fridays at 16:00</div>
        </div>
        <div className="sc-label dt-in" data-on={on(s, 5)}>
          <span>Activity</span>
        </div>
        <div className="dt-card sc-card dt-in" data-on={on(s, 5)}>
          <div className="dt-card-top">
            <span className="dt-badge">R</span>
            <span className="dt-card-project">Relay</span>
            <span className="dt-swap" data-on={on(s, 6)}>
              <span className="dt-card-state" data-kind="working">
                12s <span className="dt-spin" />
              </span>
              <span className="dt-card-state" data-kind="unread">
                <i /> now
              </span>
            </span>
          </div>
          <span className="dt-card-title">Release notes since v0.30.0</span>
          <div className="dt-card-meta">
            <Cycle at={s >= 6 ? 1 : 0} items={["Reading the week's merges…", "Wrote the notes for 0.31.0"]} />
          </div>
        </div>
      </div>
      <Cursor x={s >= 1 ? 200 : 250} y={s >= 1 ? 150 : 200} show={s >= 1 && s < 3} press={is(within(s, 2, 3))} />
    </div>
  );
}

/* ---- Drive the threads on your other computers ---- */
// A paired computer's own threads sit in Activity with its name under them
// (ComputerThreadCard.tsx); open one and you answer it from here.
export const computersLoop: Loop = {
  times: [0, 900, 1700, 2300, 3300, 4700, 5300, 7600],
  still: 6,
};
const away = [
  { title: "Nightly import keeps timing out", where: "macmini", state: "waiting" },
  { title: "Bump Electron to 40", where: "macmini", state: "working" },
  { title: "Fix flaky sync test", where: undefined, state: "9m" },
] as const;
const answer = "Split it into batches";
export function ComputersTile({ s }: { s: number }) {
  const opened = s >= 3;
  const sent = s >= 6;
  return (
    <div className="pc-tile">
      <div className="pc-list" data-gone={opened ? "" : undefined}>
        {away.map(({ title, where, state }, i) => (
          <div className="dt-card pc-card" data-hover={i === 0 ? within(s, 1, 3) : undefined} key={title}>
            <div className="dt-card-top">
              <span className="dt-badge">R</span>
              <span className="dt-card-project">Relay</span>
              <CardState state={state} />
            </div>
            <span className="dt-card-title">{title}</span>
            {where ? (
              <div className="dt-card-meta">
                <Monitor size={11} /> {where}
              </div>
            ) : null}
          </div>
        ))}
      </div>
      <div className="pc-thread dt-in" data-on={on(s, 3)}>
        <div className="t-hcard pc-where">
          <div className="t-hcard-head">
            <span className="t-hwhere">
              <span>
                <Server size={13} /> macmini
              </span>
            </span>
          </div>
          <div className="t-hstatus">
            <span className="t-hload">
              <i />
              <b>23%</b> CPU <b>9.1</b>/16 GB
            </span>
          </div>
        </div>
        <p className="dt-say">The import times out on the biggest tenants. Raise the limit to 120s, or split the import into batches?</p>
        <p className="dt-bubble dt-in pc-sent" data-on={on(s, 6)}>
          {answer}
        </p>
        <p className="dt-say pc-line dt-in" data-on={on(s, 6)}>
          <span className="dt-spin" /> Splitting the import into batches…
        </p>
        <div className="dt-composer pc-composer">
          <span className="dt-swap t-grow" data-on={within(s, 4, 6)}>
            <Placeholder>{PLACEHOLDER.idle}</Placeholder>
            <span className="t-nowrap">
              <Type text={answer} go={s >= 4 && !sent} ms={900} />
            </span>
          </span>
          <span className="dt-send" data-press={within(s, 5, 6)}>
            <ArrowUp size={13} strokeWidth={2.5} />
          </span>
        </div>
      </div>
      <Cursor
        x={s >= 4 ? 268 : s >= 1 ? 170 : 240}
        y={s >= 4 ? 210 : s >= 1 ? 50 : 140}
        show={(s >= 1 && s < 3) || within(s, 4, 6) !== undefined}
        press={is(within(s, 2, 3)) || is(within(s, 5, 6))}
      />
    </div>
  );
}

function CardState({ state }: { state: (typeof away)[number]["state"] }) {
  if (state === "waiting")
    return (
      <span className="dt-card-state" data-kind="waiting">
        <i /> Needs input
      </span>
    );
  if (state === "working")
    return (
      <span className="dt-card-state" data-kind="working">
        <span className="dt-spin" />
      </span>
    );
  return <span className="dt-card-state">{state}</span>;
}
