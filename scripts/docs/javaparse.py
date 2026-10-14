#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
A small, tolerant reader for the Java in this repository: types (class, interface, record, enum, nested too),
their fields / record components / enum constants, method signatures, annotations and javadoc. It is NOT a Java
parser: it understands the shapes this code base uses (no generics bounds in signatures worth keeping, no inner
anonymous classes in headers) and skips what it does not recognise. Used by generate_docs.py only; no network, no
database, standard library only.
"""
import re
from collections import namedtuple

Member = namedtuple("Member", "kind name type params visibility static annotations doc")
# kind: field | component | constant | method | constructor
Param = namedtuple("Param", "type name annotations")


class JType:
    def __init__(self, kind, name, package, outer=None):
        self.kind, self.name, self.package, self.outer = kind, name, package, outer
        self.modifiers = []
        self.annotations = []           # [(name, argument text)]
        self.extends = []
        self.implements = []
        self.doc = ""
        self.members = []
        self.nested = []
        self.imports = {}               # simple name -> fully qualified (de.dahaem.* only), file level
        self.file = None

    @property
    def qualified(self):
        return f"{self.package}.{self.local_name}"

    @property
    def local_name(self):
        return f"{self.outer.local_name}.{self.name}" if self.outer else self.name

    def annotation(self, name):
        return next((a for a in self.annotations if a[0] == name), None)


# ---------- cleaning ----------
def _blank(match):
    return re.sub(r"[^\n]", " ", match.group(0))


def clean(source):
    """-> (text, javadocs). Strings, chars and comments are blanked; each javadoc becomes \\x00<n>\\x00 padded
    with spaces, and its text is kept in javadocs[n]."""
    javadocs = []
    out, i, n = [], 0, len(source)
    while i < n:
        c = source[i]
        if source.startswith("/**", i) and not source.startswith("/**/", i):
            j = source.index("*/", i + 3) + 2
            javadocs.append(source[i:j])
            out.append(f"\x00{len(javadocs) - 1}\x00")
            i = j
        elif source.startswith("/*", i):
            j = source.index("*/", i + 2) + 2
            out.append(" ")
            i = j
        elif source.startswith("//", i):
            j = source.find("\n", i)
            j = n if j < 0 else j
            i = j
        elif source.startswith('"""', i):
            j = source.index('"""', i + 3) + 3
            out.append('""')
            i = j
        elif c == '"':
            j = i + 1
            while j < n and source[j] != '"':
                j += 2 if source[j] == "\\" else 1
            out.append('""')
            i = j + 1
        elif c == "'":
            j = i + 1
            while j < n and source[j] != "'":
                j += 2 if source[j] == "\\" else 1
            out.append("''")
            i = j + 1
        else:
            out.append(c)
            i += 1
    return "".join(out), javadocs


def doc_text(raw):
    """Javadoc comment -> plain text paragraphs (tags like {@code x} reduced to x, <li> to bullets)."""
    body = re.sub(r"^/\*\*|\*/$", "", raw.strip())
    lines = [re.sub(r"^\s*\*\s?", "", ln) for ln in body.split("\n")]
    text = "\n".join(lines).strip()
    text = re.sub(r"\{@(?:code|link|linkplain|literal)\s+([^}]*)\}", lambda m: m.group(1).replace("#", "."), text)
    text = re.sub(r"\{@\w+\s+([^}]*)\}", r"\1", text)
    text = re.sub(r"</?(?:p|ul|ol|br\s*/?)>", "\n", text)
    text = re.sub(r"<li>\s*", "\n- ", text)
    text = re.sub(r"</li>", "", text)
    text = re.sub(r"<(/?)(?:b|strong)>", "**", text)
    text = re.sub(r"</?(?:i|em)>", "*", text)
    text = re.sub(r"<[^>]+>", "", text)
    text = text.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
    text = re.sub(r"^@\w+.*$", "", text, flags=re.M)        # @param / @return / @throws lines
    text = re.sub(r"[ \t]+\n", "\n", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def first_sentence(text):
    flat = " ".join(text.split())
    m = re.match(r"(.+?[.!?])(?:\s|$)", flat)
    return (m.group(1) if m else flat)[:240]


# ---------- scanning ----------
def match_close(text, start, open_ch, close_ch):
    """Index of the bracket closing the one at text[start]."""
    depth = 0
    for i in range(start, len(text)):
        if text[i] == open_ch:
            depth += 1
        elif text[i] == close_ch:
            depth -= 1
            if depth == 0:
                return i
    return len(text) - 1


def split_top(text, sep=","):
    """Split on sep at bracket depth 0 (<>, (), [], {})."""
    parts, depth, cur = [], 0, []
    for ch in text:
        if ch in "<([{":
            depth += 1
        elif ch in ">)]}":
            depth -= 1
        if ch == sep and depth == 0:
            parts.append("".join(cur))
            cur = []
        else:
            cur.append(ch)
    if "".join(cur).strip():
        parts.append("".join(cur))
    return parts


def take_annotations(head):
    """-> (annotations [(name, args)], head without them)."""
    annotations, out, i = [], [], 0
    while i < len(head):
        if head[i] == "@" and not head.startswith("@interface", i):
            m = re.match(r"@([\w.]+)", head[i:])
            j = i + m.end()
            args = ""
            k = j
            while k < len(head) and head[k] == " ":
                k += 1
            if k < len(head) and head[k] == "(":
                end = match_close(head, k, "(", ")")
                args = head[k + 1:end]
                j = end + 1
            annotations.append((m.group(1), args.strip()))
            i = j
        else:
            out.append(head[i])
            i += 1
    return annotations, "".join(out)


MODS = {"public", "protected", "private", "static", "final", "abstract", "default", "synchronized", "sealed",
        "non-sealed", "native", "transient", "volatile", "strictfp"}
TYPE_RE = re.compile(r"\b(class|interface|record|enum|@interface)\s+(\w+)")


def split_modifiers(head):
    words = head.split()
    mods = []
    while words and words[0] in MODS:
        mods.append(words.pop(0))
    return mods, " ".join(words)


def type_params_strip(text):
    """Drop a leading generic parameter list '<T extends X> '."""
    text = text.strip()
    if text.startswith("<"):
        text = text[match_close(text, 0, "<", ">") + 1:].strip()
    return text


def parse_params(text):
    params = []
    for part in split_top(text):
        anns, rest = take_annotations(part)
        rest = " ".join(rest.replace("final ", " ").split())
        if not rest:
            continue
        m = re.match(r"(.+?)\s+(\w+)$", rest)
        if m:
            params.append(Param(m.group(1).replace("...", "[]"), m.group(2), anns))
    return params


# ---------- the file ----------
def parse_file(path, source=None):
    if source is None:
        with open(path, encoding="utf-8") as f:
            source = f.read()
    text, javadocs = clean(source)
    pkg = re.search(r"^\s*package\s+([\w.]+)\s*;", text, re.M)
    package = pkg.group(1) if pkg else ""
    imports = {}
    for m in re.finditer(r"^\s*import\s+(?:static\s+)?(de\.dahaem\.[\w.]+)\s*;", text, re.M):
        qualified = m.group(1)
        imports[qualified.rsplit(".", 1)[1]] = qualified
    types = []
    _scan_body(text, javadocs, 0, len(text), package, None, imports, path, types, top=True)
    return types


def _doc_before(text, javadocs, pos):
    """The javadoc ending right before `pos` (only whitespace / annotations in between are not skipped here:
    callers pass the start of the head, which already includes annotations)."""
    m = re.search(r"\x00(\d+)\x00\s*$", text[max(0, pos - 24):pos])
    return doc_text(javadocs[int(m.group(1))]) if m else ""


def _scan_body(text, javadocs, start, end, package, outer, imports, path, out, top=False):
    """Walk the members of a type body (or the top level of a file) and fill `out` / outer.members."""
    i = start
    head_start = i
    while i < end:
        c = text[i]
        if c == ";":
            _member(text[head_start:i], text, javadocs, head_start, package, outer, ended_by=";")
            i += 1
            head_start = i
        elif c == "(":
            i = match_close(text, i, "(", ")") + 1
        elif c == "{":
            head = text[head_start:i]
            close = match_close(text, i, "{", "}")
            handled = _declaration(head, text, javadocs, head_start, i, close, package, outer, imports, path, out)
            if not handled:
                if "=" not in head.split("(")[0] or "(" in head.split("=")[0]:
                    _member(head, text, javadocs, head_start, package, outer, ended_by="{")
                else:
                    # field with an initializer containing braces: skip to its ';'
                    semi = text.find(";", close)
                    close = semi if semi >= 0 and semi < end else close
                    _member(head, text, javadocs, head_start, package, outer, ended_by=";")
            i = close + 1
            head_start = i
            if outer is not None and text[head_start:head_start + 1] == ";":
                head_start += 1
                i += 1
        else:
            i += 1


def _declaration(head, text, javadocs, head_start, brace, close, package, outer, imports, path, out):
    anns, plain = take_annotations(head)
    plain = re.sub(r"\x00\d+\x00", " ", plain)
    mods, rest = split_modifiers(" ".join(plain.split()))
    m = re.match(r"(class|interface|record|enum|@interface)\s+(\w+)(.*)$", rest)
    if not m:
        return False
    kind, name, tail = m.groups()
    t = JType(kind, name, package, outer)
    t.modifiers, t.annotations, t.imports, t.file = mods, anns, imports, path
    t.doc = _doc_before(text, javadocs, head_start + len(head) - len(head.lstrip())) or _doc_in(head, javadocs)
    tail = tail.strip()
    if tail.startswith("<"):
        tail = tail[match_close(tail, 0, "<", ">") + 1:].strip()
    if kind == "record" and tail.startswith("("):
        end = match_close(tail, 0, "(", ")")
        for p in parse_params(tail[1:end]):
            t.members.append(Member("component", p.name, p.type, [], "public", False, p.annotations, ""))
        tail = tail[end + 1:].strip()
    ext = re.search(r"\bextends\s+(.+?)(?=\bimplements\b|\bpermits\b|$)", tail)
    imp = re.search(r"\bimplements\s+(.+?)(?=\bpermits\b|$)", tail)
    if ext:
        t.extends = [x.strip() for x in split_top(ext.group(1))]
    if imp:
        t.implements = [x.strip() for x in split_top(imp.group(1))]
    if outer is not None:
        outer.nested.append(t)
    out.append(t)
    body_start = brace + 1
    if kind == "enum":
        body_start = _enum_constants(t, text, javadocs, body_start, close)
    _scan_body(text, javadocs, body_start, close, package, t, imports, path, out)
    return True


def _doc_in(head, javadocs):
    m = re.search(r"\x00(\d+)\x00", head)
    return doc_text(javadocs[int(m.group(1))]) if m else ""


def _enum_constants(t, text, javadocs, start, close):
    """Constants end at the first ';' at depth 0 (or the body end). Returns where the rest of the body starts."""
    depth, i = 0, start
    while i < close:
        ch = text[i]
        if ch in "({":
            depth += 1
        elif ch in ")}":
            depth -= 1
        elif ch == ";" and depth == 0:
            break
        i += 1
    for part in split_top(text[start:i]):
        doc = _doc_in(part, javadocs)
        part = re.sub(r"\x00\d+\x00", " ", part)
        _anns, part = take_annotations(part)
        m = re.match(r"\s*(\w+)", part)
        if m:
            t.members.append(Member("constant", m.group(1), "", [], "public", True, [], doc))
    return i + 1


def _member(head, text, javadocs, head_start, package, outer, ended_by):
    if outer is None:
        return
    raw_head = head
    doc = _doc_before(text, javadocs, head_start + len(head) - len(head.lstrip())) or _doc_in(head, javadocs)
    anns, plain = take_annotations(re.sub(r"\x00\d+\x00", " ", head))
    plain = " ".join(plain.split())
    if not plain or plain.startswith(("import ", "package ")):
        return
    mods, rest = split_modifiers(plain)
    rest = type_params_strip(rest)
    if outer.kind == "interface":
        visibility = "private" if "private" in mods else "public"
    else:
        visibility = next((v for v in ("public", "protected", "private") if v in mods), "package")
    static = "static" in mods
    paren = rest.find("(")
    eq = rest.find("=")
    if paren >= 0 and (eq < 0 or paren < eq):
        before = rest[:paren].strip()
        close = match_close(rest, paren, "(", ")")
        params = parse_params(rest[paren + 1:close])
        names = before.split()
        if not names:
            return
        name = names[-1]
        rtype = " ".join(names[:-1])
        if name == outer.name and not rtype:
            outer.members.append(Member("constructor", name, "", params, visibility, False, anns, doc))
        elif rtype:
            outer.members.append(Member("method", name, rtype, params, visibility, static, anns, doc))
        elif outer.kind == "record" and name == outer.name:      # compact constructor
            return
        return
    # field: "Type name [= value]"
    decl = rest.split("=")[0].strip()
    m = re.match(r"(.+?)\s+(\w+)$", decl)
    if m:
        outer.members.append(Member("field", m.group(2), m.group(1), [], visibility, static, anns, doc))


# ---------- helpers for the generators ----------
IDENT = re.compile(r"[A-Za-z_]\w*")


def referenced_names(type_text):
    """Simple type names inside 'Map<Integer, List<Foo>>' -> {'Map', 'Integer', 'List', 'Foo'}."""
    return set(IDENT.findall(type_text or ""))
