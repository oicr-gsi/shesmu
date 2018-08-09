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
syn keyword shesmuKeyword All Any By Count Default Define Distinct Dump Else EpochSecond EpochMilli First Fixed FixedConcat Flatten For Group In Input Let LexicalConcat Limit List Matches Max Min Monitor None PartitionCount Reduce Reject Reverse Run Skip Smash Sort Squish Subsample Switch Then When Where While With
syn keyword shesmuBool False True
syn match shesmuOperators '\(\~\|:\|<=\=\|>=\=\|==\|||\|-\|!=\=\|/\|\*\|&&\|\)' display
syn keyword shesmuType boolean date integer string
syn match shesmuConstant "\<\d\+\>" display
syn match shesmuConstant "\<\d\+[kMG]i\=\>" display
syn match shesmuConstant "\<\d\+\(weeks\|days\|hours\|mins\)\>" display
syn match shesmuIdentifier "\<[a-z][a-z_]*\>\s*=[^=>]" display
syn match shesmuDate "Date\s*\d\d\d\d-\d\d-\d\d\(T\d\d\:\d\d:\d\d\(Z\|[+-]\d\d\(:\d\d\)\=\)\)\=" display
syn match shesmuComment /#.*$/ contains=shesmuTodo,@Spell display
syn match shesmuRegex /\/\([^\\]*\|\\[\\\[\]AbBdDGsSwWzZ\/.]\)*\//
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
