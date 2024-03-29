" Vim syntax file
" Language:	Shesmu
" Maintainer:	OICR GSI <gsi@oicr.on.ca>
" Filenames:	*.shesmu

" Quit when a syntax file was already loaded
if exists("b:current_syntax")
    finish
endif

let s:shesmu_cpo_save = &cpo
set cpo&vim

syn case match

syn match shesmuTrailingWhite "\s\+$" containedin=ALL
syn keyword shesmuKeyword Alert
syn keyword shesmuKeyword All
syn keyword shesmuKeyword Annotations
syn keyword shesmuKeyword Any
syn keyword shesmuKeyword ArgumentType
syn keyword shesmuKeyword As
syn keyword shesmuKeyword Begin
syn keyword shesmuKeyword By
syn keyword shesmuKeyword Call
syn keyword shesmuKeyword Check
syn keyword shesmuKeyword Count
syn keyword shesmuKeyword Default
syn keyword shesmuKeyword Define
syn keyword shesmuKeyword Description
syn keyword shesmuKeyword Dict
syn keyword shesmuKeyword Distinct
syn keyword shesmuKeyword Dump
syn keyword shesmuKeyword Else
syn keyword shesmuKeyword End
syn keyword shesmuKeyword EpochMilli
syn keyword shesmuKeyword EpochSecond
syn keyword shesmuKeyword Export
syn keyword shesmuKeyword Fields
syn keyword shesmuKeyword First
syn keyword shesmuKeyword Fixed
syn keyword shesmuKeyword FixedConcat
syn keyword shesmuKeyword Flatten
syn keyword shesmuKeyword For
syn keyword shesmuKeyword Frequency
syn keyword shesmuKeyword From
syn keyword shesmuKeyword Function
syn keyword shesmuKeyword Group
syn keyword shesmuKeyword If
syn keyword shesmuKeyword IfDefined
syn keyword shesmuKeyword Import
syn keyword shesmuKeyword In
syn keyword shesmuKeyword Input
syn keyword shesmuKeyword InputType
syn keyword shesmuKeyword IntersectionJoin
syn keyword shesmuKeyword Into
syn keyword shesmuKeyword Join
syn keyword shesmuKeyword Label
syn keyword shesmuKeyword Labels
syn keyword shesmuKeyword LeftIntersectionJoin
syn keyword shesmuKeyword LeftJoin
syn keyword shesmuKeyword Let
syn keyword shesmuKeyword LexicalConcat
syn keyword shesmuKeyword Limit
syn keyword shesmuKeyword List
syn keyword shesmuKeyword Match
syn keyword shesmuKeyword Location
syn keyword shesmuKeyword Max
syn keyword shesmuKeyword Min
syn keyword shesmuKeyword Monitor
syn keyword shesmuKeyword None
syn keyword shesmuKeyword Olive
syn keyword shesmuKeyword OnReject
syn keyword shesmuKeyword OnlyIf
syn keyword shesmuKeyword PartitionCount
syn keyword shesmuKeyword Pick
syn keyword shesmuKeyword Prefix
syn keyword shesmuKeyword Reduce
syn keyword shesmuKeyword Refill
syn keyword shesmuKeyword Reject
syn keyword shesmuKeyword Remainder
syn keyword shesmuKeyword Require
syn keyword shesmuKeyword RequiredServices
syn keyword shesmuKeyword Return
syn keyword shesmuKeyword ReturnType
syn keyword shesmuKeyword Reverse
syn keyword shesmuKeyword Run
syn keyword shesmuKeyword Skip
syn keyword shesmuKeyword Sort
syn keyword shesmuKeyword Splitting
syn keyword shesmuKeyword Squish
syn keyword shesmuKeyword Stats
syn keyword shesmuKeyword Subsample
syn keyword shesmuKeyword Sum
syn keyword shesmuKeyword Switch
syn keyword shesmuKeyword Tag
syn keyword shesmuKeyword Univalued
syn keyword shesmuKeyword Then
syn keyword shesmuKeyword Timeout
syn keyword shesmuKeyword To
syn keyword shesmuKeyword Tuple
syn keyword shesmuKeyword TypeAlias
syn keyword shesmuKeyword Using
syn keyword shesmuKeyword Version
syn keyword shesmuKeyword When
syn keyword shesmuKeyword Where
syn keyword shesmuKeyword While
syn keyword shesmuKeyword With
syn keyword shesmuKeyword Without
syn keyword shesmuKeyword Zipping
syn keyword shesmuBool False True
syn keyword shesmuConstant json_signature
syn keyword shesmuConstant sha1_signature
syn keyword shesmuConstant signature_names
syn match shesmuOperators '\(`\|\~\|:\|<=\=\|>=\=\|==\|=~\|||\|-\|!=\=\|/\|\*\|&&\|?\)' display
syn keyword shesmuType boolean date float integer json path string
syn match shesmuConstant "\<\d\+\>" display
syn match shesmuConstant "\<\d\+[kMG]i\=\>" display
syn match shesmuConstant "\<\d\+\(weeks\|days\|hours\|mins\)\>" display
syn match shesmuConstant "'\([^\\']\|\\'\)\+\'" display
syn match shesmuConstant "[A-Z][A-Z_0-9]\+" display
syn match shesmuIdentifier "\<[a-z][a-zA-Z0-9_]*\>\s*=[^=>]" display
syn match shesmuDate "Date\s*\d\d\d\d-\d\d-\d\d\(T\d\d\:\d\d:\d\d\(Z\|[+-]\d\d\(:\d\d\)\=\)\)\=" display
syn match shesmuComment /#.*$/ contains=shesmuTodo,@Spell display
syn match shesmuRegex /\/\([^\\/]*\|\\[\\\[\]AbBdDGsSwWzZ\/.]\)*\/[ceimsu]*/
syn match shesmuDelimiter contained containedin=shesmuRegex "\~"
syn match shesmuDelimiter contained containedin=shesmuRegex "?"
syn match shesmuDelimiter contained containedin=shesmuRegex "\*"
syn match shesmuDelimiter contained containedin=shesmuRegex "\."
syn match shesmuDelimiter contained containedin=shesmuRegex "/"
syn match shesmuDelimiter contained containedin=shesmuRegex "("
syn match shesmuDelimiter contained containedin=shesmuRegex ")"
syn match shesmuDelimiter contained containedin=shesmuRegex "\["
syn match shesmuDelimiter contained containedin=shesmuRegex "\]"
syn match shesmuDelimiter contained containedin=shesmuRegex "\\."

syn region shesmuString matchgroup=shesmuDelimiter start='"' end='"' skip='\\"' contains=shesmuEscape,shesmuEscapeError,shesmuInterpolation,@Spell
syn match shesmuEscapeError contained "\\." display
syn match shesmuEscapeError "\\}" display
syn match shesmuEscape contained containedin=shesmuString '\\[nt"\\{]' display
syn region shesmuInterpolation contained contains=TOP matchgroup=shesmuDelimiter start="{" end="}"

syn keyword shesmuTodo contained containedin=shesmuComment TODO FIXME XXX

hi def link shesmuBool		Boolean
hi def link shesmuConstant		Constant
hi def link shesmuKeyword		Statement
hi def link shesmuOperators	Operator
hi def link shesmuRegex	Operator
hi def link shesmuTrailingWhite	DiffDelete
hi def link shesmuType		Type
hi def link shesmuIdentifier	Identifier
hi def link shesmuDate		Constant
hi def link shesmuString		String
hi def link shesmuDelimiter	Delimiter
hi def link shesmuEscape		SpecialChar
hi def link shesmuEscapeError	Error
hi def link shesmuComment		Comment
hi def link shesmuTodo		Todo

let b:current_syntax = "shesmu"

let &cpo = s:shesmu_cpo_save
unlet s:shesmu_cpo_save

" vim: ts=8
