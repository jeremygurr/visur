let ca;
let editorContent;
let span;
let canvasStart;
let canvasEnd;
let cursorQuantumStart;
let cursorQuantumEnd;
let cursorQuantum;
let isAtQuantumStart;
let scopeQuantum;
let searchTarget;

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
let canvasWidth = Math.floor(canvas.width / cellWidth)
let canvasHeight = Math.floor(canvas.height / cellHeight)

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
    ca = message["body"]["ca"]
    editorContent = message["body"]["editorContent"]
    span = message["body"]["span"]
    canvasStart = message["body"]["canvasStart"]
    canvasEnd = message["body"]["canvasEnd"]
    cursorQuantumStart = message["body"]["cursorQuantumStart"]
    cursorQuantumEnd = message["body"]["cursorQuantumEnd"]
    mode = message["body"]["editorMode"]
    submode = message["body"]["editorSubmode"]
    cursorQuantum = message["body"]["cursorQuantum"]
    isAtQuantumStart = message["body"]["isAtQuantumStart"]
    scopeQuantum = message["body"]["scopeQuantum"]
    isInCommandState = message["body"]["isInCommandState"]
    commandStateContent = message["body"]["commandStateContent"]
    commandCursor = message["body"]["commandCursor"]
    searchTarget = message["body"]["searchTarget"]

    console.log("quantum start = " + cursorQuantumStart)
    console.log("quantum end = " + cursorQuantumEnd)

//    console.log("Content = " + (editorContent))

    clearCanvas()
    ctx.fillStyle = "black"
    drawCanvas()
    document.getElementById("cursorQuantumDisplay").innerHTML = cursorQuantum.toUpperCase() + " QUANTUM"
    if(isInCommandState) {
      let commandStateDisplay = ";"
      let commandCursorWasDrawn = false;
      for(let x = 0; x < commandStateContent.length; x++) {
        if(x == commandCursor) {
          commandStateDisplay += "|";
          commandCursorWasDrawn = true;
        } else {
          commandStateDisplay += " ";
        }
        commandStateDisplay += commandStateContent[x];
      }
      if(!commandCursorWasDrawn) {
        commandStateDisplay += "|"
      }
      document.getElementById("currentEditorModeDisplay").innerHTML = commandStateDisplay
    } else if(mode == submode) {
      document.getElementById("currentEditorModeDisplay").innerHTML = mode.toUpperCase() + " MODE"
    } else {
      document.getElementById("currentEditorModeDisplay").innerHTML = submode.toUpperCase() + " SUBMODE";
    }
    if(submode == "searchForward" || submode == "searchBackward") {
      document.getElementById("currentEditorModeDisplay").innerHTML = " " + scopeQuantum.toUpperCase()
      if(submode == "searchForward") {
        document.getElementById("currentEditorModeDisplay").innerHTML += " (f) "
      } else {
        document.getElementById("currentEditorModeDisplay").innerHTML += " (F) "
      }
      document.getElementById("currentEditorModeDisplay").innerHTML += searchTarget
    } else {
      document.getElementById("currentEditorModeDisplay").innerHTML += "; SPAN = " + span
    }
  })

  let canvasInfo = {
    width: (canvas.width - cxContentOffset) / cellWidth + 1,
    height: (canvas.height - cyContentOffset) / cellHeight + 1
  };
  /*canvasWasChangedEventComplete ensures that canvasWasChanged gets called before modelWasChanged so that
  getCanvasWidth is not called before putCanvasWidth */
  eb.registerHandler("modelWasChanged", (error, message) => {
    console.log("modelWasChanged message: " + JSON.stringify(message));
    eb.send("modelWasChanged", null)
  });
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

function drawCanvas() {
  let drawContentX = 0
  let drawContentY = 0
  lineContinuing = false
  let x = 0
  let y = 0
  let numberOfWrappedLines = 0
  contentLoop:
  for(let absX = canvasStart; absX <= canvasEnd; absX++) {
    if(span > 0) {
      if(absX >= cursorQuantumStart && absX < cursorQuantumEnd) {
        drawCursor(x, y, false)
      } else if(absX == cursorQuantumStart && absX == cursorQuantumEnd) {
        drawCursor(x, y, true)
      }
    } else {
      if(isAtQuantumStart) {
        if(absX == cursorQuantumStart) {
          drawCursor(x, y, true)
        }
      } else {
        if(absX == cursorQuantumEnd) {
          drawCursor(x, y, true)
        }
      }
    }
    if(absX < editorContent.length) {
      characterToDraw = editorContent[absX];
      drawCharacter(x, y, characterToDraw);
      x++
    }
    let isAtEndOfCurrentLine = x % canvasWidth == 0
    if(editorContent[absX] === "\n" || isAtEndOfCurrentLine) {
      y++
      x = 0
    }
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

function drawCharacter(x, y, characterToDraw) {
  ctx.fillText(characterToDraw, toXContent(x), toYContent(y));
}

function drawCursor(x, y, spanEqualsZero) {
  if(spanEqualsZero) {
    ctx.fillText("|", toXCursorSpanZero(x), toYContent(y))
  } else {
    const rectWidth = cellWidth;
    const rectHeight = cellHeight;
    ctx.fillStyle = "#FFD700";
    ctx.fillRect(toXCursorSpanGreaterThanZero(x), toYCursor(y) - cellHeight, rectWidth, rectHeight);
    ctx.fillStyle = "black";
  }
}

function clearCanvas() {
  ctx.clearRect(0, 0, canvas.width, canvas.height);
}
