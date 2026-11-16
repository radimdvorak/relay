import { CornerDownRight, MessageCircleQuestion } from "lucide-react";
import type { AskedQuestion } from "../../../shared/asked-questions";
import "./answered-questions.css";

/** The same saved question/answer row for native calls and async cards. */
export function AnsweredQuestions({
  questions = [],
  label = `Asked you ${questions.length} ${questions.length === 1 ? "question" : "questions"}`,
  status,
  note,
}: {
  /** Left out when the label already says all of it, like a declined secret. */
  questions?: AskedQuestion[];
  label?: string;
  status?: string;
  /** One line under the label, in place of questions and answers. */
  note?: string;
}) {
  if (!questions.length)
    return (
      <div className="answered-questions">
        <div className="answered-questions-heading">
          <MessageCircleQuestion size={14} aria-hidden />
          <span>{label}</span>
          {status && <span role="status">{status}</span>}
        </div>
        {note && <div className="answered-questions-body">{note}</div>}
      </div>
    );
  return (
    <details className="answered-questions" open>
      <summary className="answered-questions-heading">
        <MessageCircleQuestion size={14} aria-hidden />
        <span>{label}</span>
        {status && <span role="status">{status}</span>}
      </summary>
      <div className="answered-questions-body">
        {questions.map((q, i) => (
          <div key={i}>
            <div>{q.question}</div>
            <div className="answered-question-answer">
              <CornerDownRight size={12} aria-hidden />
              <span>{q.answer}</span>
            </div>
          </div>
        ))}
      </div>
    </details>
  );
}
