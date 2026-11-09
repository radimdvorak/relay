/** One line of a file at a revision, as blame asks for it. */
export interface BlameQuery {
  revision: string;
  path: string;
  line: number;
}

/** The commit that last changed a line, and who wrote it. */
export interface LineBlame {
  commit: string;
  author: string;
  email: string;
  authoredAt: string;
  summary: string;
  shallow: boolean;
}
