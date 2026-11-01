/* One viewWasChanged message is one complete viewport. This file holds no editor state and no derived layout:
   rows arrive already wrapped, carrying their own origins, and the cursor arrives as a row/column into them. */

let view = null

let canvas = document.getElementById("mainCanvas")
if((window.innerWidth - 5) % 21 == 0) {
  canvas.width = window.innerWidth
} else {
  canvas.width = window.innerWidth - (window.innerWidth - 5) % 21 //words get cut off when changing window size if I don't do this
}
canvas.height = window.innerHeight
console.log("canvas width = " + canvas.width)
console.log("canvas height = " + canvas.height)

let ctx = canvas.getContext("2d")
ctx.font = "20px courier"
const textWidth = ctx.measureText("c").width; //measure the width of an example char

let cellWidth = textWidth + 1
let cellHeight = 20

let cxContentOffset = 6
let cxCursorOffset = 5
let cyContentOffset = 20
let cyCursorOffset = 24

let numberOfTimesPageWasLoaded = 0

function refreshEditor() {
  numberOfTimesPageWasLoaded++
  console.log("editor refreshed " + numberOfTimesPageWasLoaded + " times")
}

document.getElementById("visurEditor").onload = function() { refreshEditor() }

var eb = new EventBus('http://localhost:8888/eventbus');
eb.onopen = function() {
  console.log("connection established with js' event bus")
  eb.registerHandler('viewWasChanged', (error, message) => {
    // Nothing here caches rows, so "full: true" (discard cached rows, do not diff) holds by construction.
    view = message["body"]
    console.log("origin = " + view.origin + ", " + view.rows.length + " rows")
    drawView()
    drawModeLine()
  })

  let canvasInfo = {
    width: (canvas.width - cxContentOffset) / cellWidth + 1,
    height: (canvas.height - cyContentOffset) / cellHeight + 1
  };
  eb.send("canvasWasChanged", JSON.stringify(canvasInfo));
}

function sendKeyPressedEvent(keyPressed) {
  console.log("keyPressed = " + keyPressed)
  let keyPressedInfo = {
    key: keyPressed
  }
  eb.send("keyWasPressed", JSON.stringify(keyPressedInfo))
  console.log(keyPressedInfo.key + " key was pressed")
}

document.addEventListener('keydown', (event) => {
  sendKeyPressedEvent(event.key)
})

function drawView() {
  clearCanvas()
  ctx.fillStyle = "black"
  let selectionWasPainted = drawSpan()
  for(let row = 0; row < view.rows.length; row++) {
    ctx.fillText(view.rows[row].text, toXContent(0), toYContent(row))
  }
  drawCaret(selectionWasPainted)
}

/* The selection is an absolute range; each row clips it to its own characters. Returns whether any cell was
   highlighted: a selection past the last character of the document covers none. */
function drawSpan() {
  let painted = false
  if(view.span == 0) {
    return painted
  }
  for(let row = 0; row < view.rows.length; row++) {
    let rowStart = view.rows[row].start
    let rowEnd = rowStart + view.rows[row].text.length
    let from = Math.max(view.cursorQuantumStart, rowStart)
    let to = Math.min(view.cursorQuantumEnd, rowEnd)
    if(from >= to) {
      continue
    }
    ctx.fillStyle = "#FFD700"
    ctx.fillRect(toXCursorSpanGreaterThanZero(from - rowStart), toYCursor(row) - cellHeight,
                 (to - from) * cellWidth, cellHeight)
    ctx.fillStyle = "black"
    painted = true
  }
  return painted
}

/* With a selection the block cursor is part of drawSpan; otherwise the cursor is a thin bar, placed here. The
   bar also stands in when the selection covers no visible cell, so the screen always shows where the cursor is. */
function drawCaret(selectionWasPainted) {
  if(view.span > 0 && selectionWasPainted) {
    return
  }
  if(view.rows.length == 0) {
    return //empty document: blank screen, which is what the empty rows already drew
  }
  let row = clamp(view.cursorRow, 0, view.rows.length - 1)
  let col = clamp(view.cursorCol, 0, view.rows[row].text.length)
  ctx.fillText("|", toXCursorSpanZero(col), toYContent(row))
}

function clamp(value, low, high) {
  return Math.min(Math.max(value, low), high)
}

function drawModeLine() {
  document.getElementById("cursorQuantumDisplay").innerHTML = view.cursorQuantum.toUpperCase() + " QUANTUM"
  if(view.isInCommandState) {
    let commandStateDisplay = ";"
    let commandCursorWasDrawn = false;
    for(let x = 0; x < view.commandStateContent.length; x++) {
      if(x == view.commandCursor) {
        commandStateDisplay += "|";
        commandCursorWasDrawn = true;
      } else {
        commandStateDisplay += " ";
      }
      commandStateDisplay += view.commandStateContent[x];
    }
    if(!commandCursorWasDrawn) {
      commandStateDisplay += "|"
    }
    document.getElementById("currentEditorModeDisplay").innerHTML = commandStateDisplay
  } else if(view.editorMode == view.editorSubmode) {
    document.getElementById("currentEditorModeDisplay").innerHTML = view.editorMode.toUpperCase() + " MODE"
  } else {
    document.getElementById("currentEditorModeDisplay").innerHTML = view.editorSubmode.toUpperCase() + " SUBMODE";
  }
  if(view.editorSubmode == "searchForward" || view.editorSubmode == "searchBackward") {
    document.getElementById("currentEditorModeDisplay").innerHTML = " " + view.scopeQuantum.toUpperCase()
    if(view.editorSubmode == "searchForward") {
      document.getElementById("currentEditorModeDisplay").innerHTML += " (f) "
    } else {
      document.getElementById("currentEditorModeDisplay").innerHTML += " (F) "
    }
    document.getElementById("currentEditorModeDisplay").innerHTML += view.searchTarget + "_"
  } else {
    document.getElementById("currentEditorModeDisplay").innerHTML += "; SPAN = " + view.span
  }
}

function toXContent(x) {
  return x * cellWidth + cxContentOffset
}

function toXCursorSpanZero(x) {
  return x * cellWidth - 0.5
}

function toXCursorSpanGreaterThanZero(x) {
  return x * cellWidth + cxCursorOffset
}

function toYContent(y) {
  return y * cellHeight + cyContentOffset
}

function toYCursor(y) {
  return y * cellHeight + cyCursorOffset
}

function clearCanvas() {
  ctx.clearRect(0, 0, canvas.width, canvas.height);
}
