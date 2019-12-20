
use crate::*;

impl Blank {
    const REPR:char = '_';
}
impl Number {
    const BASE_SEPARATOR:char = '_';
}
impl Mod {
    const SUFFIX:char = '=';
}
/// Symbol enclosing raw Text line.
const FMT_QUOTE:char = '\'';

/// Symbol enclosing formatted Text line.
const RAW_QUOTE:char = '"';

/// Symbol used to break lines in Text block.
const NEWLINE:char = '\n';

/// Symbol introducing escape segment in the Text.
const BACKSLASH:char = '\\';

/// Symbol enclosing expression segment in the formatted Text.
const EXPR_QUOTE:char = '`';

/// Symbol that introduces UTF-16 code in the formatted Text segment.
const UNICODE16_INTRODUCER:char = 'u';

/// String that opens "UTF-21" code in the formatted Text segment.
const UNICODE21_OPENER:&str = "u{";

/// String that closese "UTF-21" code in the formatted Text segment.
const UNICODE21_CLOSER:&str = "}";

/// Symbol that introduces UTF-16 code in the formatted Text segment.
const UNICODE32_INTRODUCER:char = 'U';

impl TextBlockRaw {
    const QUOTE:&'static str = "\"\"\"";
}
impl<T> TextBlockFmt<T> {
    const QUOTE:&'static str = "'''";
}
///////////////////////////////////////


// ===============
// === Builder ===
// ===============
make_repr!(Empty);
make_repr!(Letter, self.char);
make_repr!(Space , self);
make_repr!(Text  , self.str);
make_repr!(Seq   , self.first, self.second);


// =====================
// === TextBlockLine ===
// =====================
/// Not an instance of `HasSpan`, as it needs to know parent block's offset.
impl<T: HasSpan> TextBlockLine<T> {
    fn span(&self, block_offset: usize) -> usize {
        let line_count              = self.empty_lines.len() + 1;
        let empty_lines_space:usize = self.empty_lines.iter().sum();
        let line_breaks             = line_count * NEWLINE.span();
        empty_lines_space + line_breaks + block_offset + self.text.span()
    }
}
impl<T: HasRepr> TextBlockLine<T> {
    fn repr(&self, block_offset: usize) -> String {
        let empty_lines = self.empty_lines.iter().map(|line| {
            NEWLINE.to_string() + &line.repr()
        }).collect_vec();
        let line_pfx = NEWLINE.to_string() + &block_offset.repr();
        empty_lines.repr() + &line_pfx.repr() + &self.text.repr()
    }
}

// =====================
// === Text Segments ===
// =====================
make_repr!(SegmentPlain, self.value);
make_repr!(SegmentRawEscape, BACKSLASH, self.code);
make_repr!(SegmentExpr<T>, EXPR_QUOTE, self.value, EXPR_QUOTE);
make_repr!(SegmentEscape, BACKSLASH, self.code);

// =================
// === RawEscape ===
// =================
make_repr!(Unfinished);
make_repr!(Invalid, self.str);
make_repr!(Slash, BACKSLASH);
make_repr!(Quote, FMT_QUOTE);
make_repr!(RawQuote, RAW_QUOTE);

// ==============
// === Escape ===
// ==============
make_repr!(EscapeCharacter, self.c     );
make_repr!(EscapeControl  , self.name  );
make_repr!(EscapeNumber   , self.digits);
make_repr!(EscapeUnicode16, UNICODE16_INTRODUCER, self.digits);
make_repr!(EscapeUnicode21, UNICODE21_OPENER    , self.digits, UNICODE21_CLOSER);
make_repr!(EscapeUnicode32, UNICODE32_INTRODUCER, self.digits);

// =============
// === Block ===
// =============
make_repr!(BlockLine<T>, self.elem, self.off);

// =============
// === Macro ===
// =============

// === Match ==
impl<T: HasSpan> HasSpan for Match<T> {
    fn span(&self) -> usize {
        let pfx = self.pfx.span();
        let segs = self.segs.span();
        pfx + segs
    }
}

impl<T: HasRepr> HasRepr for Match<T> {
    fn repr(&self) -> String {
        let pfx_items = self.pfx.as_ref().map(|pfx| pfx.iter().collect_vec());
        let pfx_items = pfx_items.unwrap_or(Vec::new());
        let pfx_reprs = pfx_items.iter().map(|sast| {
            sast.wrapped.repr() + &sast.off.repr()
        }).collect_vec();

        return pfx_reprs.repr() + &self.segs.repr()
    }
}

// === Ambiguous ==
make_repr!(Ambiguous, self.segs);


// === Macro Segments ==
make_repr!(MacroMatchSegment<T> , self.head, self.body);
make_repr!(MacroAmbiguousSegment, self.head, self.body);

// === MacroPatternMatch subtypes ===
make_repr!(MacroPatternMatchRawBegin  );
make_repr!(MacroPatternMatchRawEnd    );
make_repr!(MacroPatternMatchRawNothing);
make_repr!(MacroPatternMatchRawSeq    <T>, self.elem);
make_repr!(MacroPatternMatchRawOr     <T>, self.elem);
make_repr!(MacroPatternMatchRawMany   <T>, self.elem);
make_repr!(MacroPatternMatchRawExcept <T>, self.elem);
make_repr!(MacroPatternMatchRawBuild  <T>, self.elem);
make_repr!(MacroPatternMatchRawErr    <T>, self.elem);
make_repr!(MacroPatternMatchRawTag    <T>, self.elem);
make_repr!(MacroPatternMatchRawCls    <T>, self.elem);
make_repr!(MacroPatternMatchRawTok    <T>, self.elem);
make_repr!(MacroPatternMatchRawBlank  <T>, self.elem);
make_repr!(MacroPatternMatchRawVar    <T>, self.elem);
make_repr!(MacroPatternMatchRawCons   <T>, self.elem);
make_repr!(MacroPatternMatchRawOpr    <T>, self.elem);
make_repr!(MacroPatternMatchRawMod    <T>, self.elem);
make_repr!(MacroPatternMatchRawNum    <T>, self.elem);
make_repr!(MacroPatternMatchRawText   <T>, self.elem);
make_repr!(MacroPatternMatchRawBlock  <T>, self.elem);
make_repr!(MacroPatternMatchRawMacro  <T>, self.elem);
make_repr!(MacroPatternMatchRawInvalid<T>, self.elem);

// === Shifted ===
make_repr!(Shifted<T>, self.off, self.wrapped);
make_repr!(ShiftedVec1<T>, self.head, self.tail);


// =============
// === Shape ===
// =============

/// Helper to represent that optional number base has additional character.
struct NumberBase<T>(T);
make_repr!(NumberBase<T>, self.0, Number::BASE_SEPARATOR);

/// Helper to represent line with additional spacing prepended.
struct Indented<T>(usize,T);
make_repr!(Indented<T>, self.0, self.1);
impl<T> Block<T> {
    fn indented<'t, U>(&self, t:&'t U) -> Indented<&'t U> {
        Indented(self.indent,t)
    }
}

make_repr!(Unrecognized    , self.str);
make_repr!(InvalidQuote    , self.quote);
make_repr!(InlineBlock     , self.quote);
make_repr!(Blank           , Blank::REPR);
make_repr!(Var             , self.name);
make_repr!(Cons            , self.name);
make_repr!(Opr             , self.name);
make_repr!(Mod             , self.name, Mod::SUFFIX);
make_repr!(InvalidSuffix<T>, self.elem, self.suffix);
make_repr!(Number          , self.base.as_ref().map(|b| NumberBase(b))
                           , self.int);
make_repr!(DanglingBase    , self.base, Number::BASE_SEPARATOR);
make_repr!(TextLineRaw     , RAW_QUOTE, self.text, RAW_QUOTE);
make_repr!(TextLineFmt<T>  , FMT_QUOTE, self.text, FMT_QUOTE);

impl HasSpan for TextBlockRaw {
    fn span(&self) -> usize {
        let lines      = self.text.iter();
        let line_spans = lines.map(|line| line.span(self.offset));
        let lines_span = line_spans.sum::<usize>();
        TextBlockRaw::QUOTE.span() + self.spaces + lines_span
    }
}
impl HasRepr for TextBlockRaw {
    fn repr(&self) -> String {
        let lines      = self.text.iter();
        let line_reprs = lines.map(|line| line.repr(self.offset));
        let line_reprs = line_reprs.collect_vec();
        TextBlockRaw::QUOTE.repr() + &self.spaces.repr() + &line_reprs.repr()
    }
}
impl<T: HasSpan> HasSpan for TextBlockFmt<T> {
    fn span(&self) -> usize {
        let lines            = self.text.iter();
        let line_spans       = lines.map(|line| line.span(self.offset));
        let lines_span:usize = line_spans.sum();
        TextBlockFmt::<T>::QUOTE.span() + self.spaces + lines_span
    }
}
impl<T: HasRepr> HasRepr for TextBlockFmt<T> {
    fn repr(&self) -> String {
        let lines            = self.text.iter();
        let line_reprs       = lines.map(|line| line.repr(self.offset));
        let line_reprs       = line_reprs.collect_vec();
        TextBlockFmt::<T>::QUOTE.repr() + &self.spaces.repr() + &line_reprs.repr()
    }
}

impl<T: HasSpan> HasSpan for TextUnclosed<T> {
    fn span(&self) -> usize {
        self.line.span() - 1 // remove missing quote
    }
}
impl<T: HasRepr> HasRepr for TextUnclosed<T> {
    fn repr(&self) -> String {
        let mut ret = self.line.repr(); // remove missing quote
        ret.pop();
        ret
    }
}

make_repr!(Prefix<T>, self.func, self.off, self.arg);
make_repr!(Infix<T>, self.larg, self.loff, self.opr, self.roff, self.rarg);
make_repr!(SectionLeft<T>, self.arg, self.off, self.opr);
make_repr!(SectionRight<T>, self.opr, self.off, self.arg);
make_repr!(SectionSides<T>, self.opr);

impl<T: HasSpan> HasSpan for Module<T> {
    fn span(&self) -> usize {
        assert!(self.lines.len() > 0);
        let break_count = self.lines.len() - 1;
        let breaks_span = break_count * NEWLINE.span();
        let lines_span = self.lines.span();
        lines_span + breaks_span
    }
}

impl<T: HasRepr> HasRepr for Module<T> {
    fn repr(&self) -> String {
        let lines_iter = self.lines.iter();
        let mut line_reprs = lines_iter.map(|line| line.repr());
        line_reprs.join(&NEWLINE.to_string())
    }
}

impl<T: HasSpan> HasSpan for Block<T> {
    fn span(&self) -> usize {
        let line_span = |line:&BlockLine<Option<T>>| {
            let indent = line.elem.as_ref().map_or(0, |_| self.indent);
            NEWLINE.span() + indent + line.span()
        };

        let head_span   = if self.is_orphan { 0 } else { 1 };
        let empty_lines = self.empty_lines.span() + self.empty_lines.len();
        let first_line  = self.indent + self.first_line.span();
        let lines       = self.lines.iter().map(line_span).sum::<usize>();
        head_span + empty_lines + first_line + lines
    }
}

impl<T: HasRepr> HasRepr for Block<T> {
    fn repr(&self) -> String {
        let head_repr         = (!self.is_orphan).as_some(NEWLINE).repr();
        let empty_lines       = self.empty_lines.iter().map(|line| {
            line.repr() + &NEWLINE.repr()
        }).collect_vec();
        let first_line = self.indented(&self.first_line);
        let tail_lines = self.lines.iter().map(|line| {
            NEWLINE.repr() + &self.indented(line).repr()
        }).collect_vec();
        head_repr + &empty_lines.repr() + &first_line.repr() + &tail_lines.repr()
    }
}

not_supported_repr!(Comment);
not_supported_repr!(Import<T>);
not_supported_repr!(Mixfix<T>);
not_supported_repr!(Group<T>);
not_supported_repr!(Def<T>);
not_supported_repr!(Foreign);
