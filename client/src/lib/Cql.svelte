<svelte:options customElement="cql-input" />

<script lang="ts">
	import Token from './Token.svelte';
	import { onMount, afterUpdate } from 'svelte';
	import { fade } from 'svelte/transition';

	const exampleQuery = '@from-date';

	onMount(async () => {
		fetchLanguageServer(exampleQuery);
	});

	let overlayOffset = 0;
	let cqlQuery = exampleQuery;
	let ast = '';
	let tokenisedChars = [];
	let currentSuggestions = undefined;
	let typeaheadOffsetPx = 0;
	let inputElement: HTMLInputElement;
  let datePickerElement: HTMLInputElement;
	let cursorMarker: HTMLSpanElement;
	let inputContainerElement: HTMLDivElement;
	let menuIndex = 0;

	const handleInput = (e: InputEvent) => {
		fetchLanguageServer(e.target.value);
	};

	const handleInputKeydown = (e) => {
		if (currentSuggestions === undefined) {
			// Only active when menu is active
			return;
		}

		let handled = true;

		switch (e.key) {
			case 'ArrowUp':
				if (menuIndex > 0) menuIndex--;
				break;
			case 'ArrowDown':
        if (currentSuggestions.suggestions.TextSuggestion) {
          if (menuIndex < currentSuggestions.suggestions.TextSuggestion.suggestions.length - 1)
					  menuIndex++;
        }
        // Focus the datepicker if it's not already focussed
        if (currentSuggestions.suggestions.DateSuggestion) {
          datePickerElement.focus();
        }
				break;
			case 'Enter':
				replaceCurrentSuggestionTargetWithMenuItem(menuIndex);
				break;
			case 'Escape':
				currentSuggestions = undefined;
				break;
			default:
				handled = false;
		}

		if (handled) {
			e.preventDefault();
		}
	};

	const handleDateKeydown = (e) => {
		switch (e.key) {
			case 'Enter':
				replaceCurrentSuggestionTarget(e.target.value);
				inputElement.focus();
				break;
			case 'Escape':
				inputElement.focus();
				break;
		}
	};

	const replaceCurrentSuggestionTargetWithMenuItem = (menuItemIndex: number) => {
		const currentSuggestion =
			currentSuggestions.suggestions.TextSuggestion.suggestions[menuItemIndex];
		const strToInsert = currentSuggestion.value;
		replaceCurrentSuggestionTarget(strToInsert);
	};

	const replaceCurrentSuggestionTarget = (strToInsert: string) => {
		const token = ast.tokens.find((token) => token.start === currentSuggestions.from);
		if (!token) {
			console.log(`No token found at ${currentSuggestions.from}`);
			return;
		}
		// Assumption here: the +1s bake in the idea of a single + or : char leading the token.
		const beforeStr = cqlQuery.slice(0, token.start + 1);
		const afterStr = cqlQuery.slice(token.end + 1);
		const applySuffix = /^\s*$/.test(afterStr);
		cqlQuery = `${beforeStr}${strToInsert}${applySuffix ? currentSuggestions.suffix : afterStr}`;

		// +1 for + or :, +1 to get us past the end of the current position
		const newCaretPosition = token.start + strToInsert.length + 2;

		// Is there a better way of waiting for Svelte to update the DOM?
		requestAnimationFrame(() => {
			inputElement.setSelectionRange(newCaretPosition, newCaretPosition);
		});

		currentSuggestions = undefined;

		fetchLanguageServer(cqlQuery);
	};

	const watchSelectionChange = () => {
		document.addEventListener('selectionchange', handleSelection);
	};

	const unwatchSelectionChange = (e) => {
		if (inputContainerElement.contains(e.relatedTarget)) {
			// We're within the input element – do not lose focus.
			return;
		}
		document.removeEventListener('selectionchange', handleSelection);
		currentSuggestions = undefined;
	};

	const handleSelection = (e: InputEvent) => {
		applyTypeahead(inputElement.selectionStart);
	};

	const applyTypeahead = (cursorOffset: number) => {
		if (!ast) {
			return;
		}

		const typeaheadCharPos = cursorOffset - 1;
		const firstValidSuggestions = ast.suggestions.find(
			(suggestion) => typeaheadCharPos >= suggestion.from && typeaheadCharPos <= suggestion.to
		);

		if (firstValidSuggestions) {
			currentSuggestions = firstValidSuggestions;
		} else {
			currentSuggestions = undefined;
		}

		// Reset the menu if the previous selection falls off the edge of the new suggestion list.
		if (
			currentSuggestions &&
			currentSuggestions.suggestions.TextSuggestion &&
			menuIndex > currentSuggestions.suggestions.TextSuggestion.suggestions.length - 1
		) {
			menuIndex = 0;
		}
	};

	afterUpdate(() => {
		typeaheadOffsetPx = cursorMarker?.getBoundingClientRect().left;
	});

	const onDatePickerInit = (datePickerEl) => {
		const { from, to } = currentSuggestions;
		const noDateYetPresent = from === to;
		if (noDateYetPresent) {
			requestAnimationFrame(() => {
				datePickerEl.focus();
			});
		}
	};

	const fetchLanguageServer = async (query: string) => {
		cqlQuery = query;
		const urlParams = new URLSearchParams();
		urlParams.append('query', query);
		const request = await fetch(`http://localhost:5050/cql?${urlParams}`);

		ast = await request.json();
		applyTokens(cqlQuery, ast.tokens);
		applyTypeahead(inputElement.selectionStart);
	};

	const applyTokens = (query, tokens) => {
		tokenisedChars = addTokensToString(query, tokens);
	};

	const addTokensToString = (str: string, tokens) => {
		return [...str].map((char, index) => {
			return {
				char,
				token: tokens.find((token) => token.start <= index && token.end >= index)
			};
		});
	};

	const syncScrollState = (e: ScrollEvent) => {
		overlayOffset = e.target.scrollLeft;
	};
</script>

<div class="Cql__input-container" bind:this={inputContainerElement}>
	<input
		class="Cql__input"
		value={cqlQuery}
		on:input={handleInput}
		on:keydown={handleInputKeydown}
		on:focus={watchSelectionChange}
		on:blur={unwatchSelectionChange}
		on:scroll={syncScrollState}
		bind:this={inputElement}
	/>
	<div class="Cql__overlay-container">
		<div class="Cql__overlay" contenteditable="true" style="left: -{overlayOffset}px">
			{#each tokenisedChars || [] as { char, token }, index}
				<Token str={char} {token} />{#if index === currentSuggestions?.from - 1}<span
						class="Cql__cursor-marker"
						bind:this={cursorMarker}
					></span>{/if}
			{/each}
		</div>
	</div>
	{#if currentSuggestions !== undefined}<div
			class={`Cql__typeahead Cql__typeahead-${Object.keys(currentSuggestions.suggestions).join('')}`}
			style="left: {typeaheadOffsetPx - 7}px"
			transition:fade={{ duration: 100 }}
		>
			{#if currentSuggestions.suggestions.TextSuggestion}
				<ul>
					{#each currentSuggestions.suggestions.TextSuggestion.suggestions as { label }, index}
						<li>
							<button
								class:--selected={menuIndex === index}
								on:click={() => replaceCurrentSuggestionTargetWithMenuItem(index)}>{label}</button
							>
						</li>
					{/each}
				</ul>
			{/if}
			{#if currentSuggestions.suggestions.DateSuggestion}
				<input
					class="Cql_typeahead-date"
					type="date"
					use:onDatePickerInit
          bind:this={datePickerElement}
					on:keydown={handleDateKeydown}
				/>
			{/if}
		</div>{/if}
	{#if ast.queryResult}<div class="Cql__output">{ast.queryResult}</div>{/if}
	{#if ast.error}<div class="Cql__error">{ast.error}</div>{/if}
</div>
<div class="debug"><pre>{JSON.stringify(ast, null, '\t')}</pre></div>

<style>
	:root {
		--input-width: 600px;
	}

	.Cql__input-container {
		position: relative;
	}

	.Cql__overlay-container {
		position: absolute;
		pointer-events: none;
		top: 7px;
		left: 7px;
		width: var(--input-width);
		overflow: hidden;
		white-space: nowrap;
	}

	.Cql__overlay {
		position: relative;
	}

	.Cql__input {
		width: var(--input-width);
		border: 2px solid #666;
		margin: 0;
		padding: 5px;
		color: transparent;
		caret-color: #333;
	}

	.Cql__input,
	.Cql__overlay-container {
		font-family: sans-serif;
		font-size: 16px;
	}

	.debug {
		background-color: #ddd;
		margin-top: 10px;
		padding: 5px;
		font-family: monospace;
		color: #333;
		white-space: pre;
	}

	.Cql__output,
	.Cql__error {
		margin-top: 1rem;
	}

	.Cql__error {
		color: darkred;
	}

	.Cql__cursor-marker {
		display: inline;
		white-space: pre;
	}
	.Cql__typeahead {
		position: absolute;
		width: 200px;
	}

	.Cql__typeahead-TextSuggestion {
		border: 2px solid grey;
		background-color: white;
	}

	.Cql__typeahead ul,
	.Cql__typeahead li {
		list-style: none;
		padding: initial;
		margin: initial;
	}

	.Cql__typeahead li button:hover {
		background-color: #eee;
		cursor: pointer;
	}

	.Cql__typeahead li button {
		width: 100%;
		padding: 5px;
		border: none;
		outline: none;
		background-color: white;
		text-align: left;
		cursor: pointer;
	}

	.Cql__typeahead li button.--selected {
		background-color: #ddd;
	}
</style>
