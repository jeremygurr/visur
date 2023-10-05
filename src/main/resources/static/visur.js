var cursorX;
var cursorY;
var content;

let canvas = document.getElementById("mainCanvas")

let cellWidth = 20
let cellHeight = 20
let canvasWidth = canvas.width / cellWidth
let canvasHeight = canvas.height / cellHeight

let canvasXOffset = 3
let canvasYOffset = 25

let ctx = canvas.getContext("2d")
ctx.font = cellWidth + "px courier"

var eb = new EventBus('http://localhost:8888/eventbus');
eb.onopen = function() {
  eb.registerHandler('viewWasChanged', (error, message) => {
    console.log("view was changed. message = " + (JSON.stringify(message)))
    cursorX = message["body"]["cursorX"]
    cursorY = message["body"]["cursorY"]
    content = message["body"]["content"]

    console.log("CursorX = " + (message["body"]["cursorX"]))
    console.log("CursorY = " + (message["body"]["cursorY"]))
    console.log("Content = " + (message["body"]["content"]))

    clearCanvas()
    drawCanvas()
    console.log("Canvas should have been drawn")

  })
  let canvasInfo = {
    width: (canvas.width - canvasXOffset) / cellWidth + 1,
    height: (canvas.height - canvasYOffset) / cellHeight + 1
  };
  eb.send("canvasWasChanged", JSON.stringify(canvasInfo))
}

document.addEventListener('keydown', (event) => {
//  var svc = new BrowserInputService(eb, "keyWasPressed");
//  svc.keyPress(event.key);
  let keyPressedInfo = {
    key: event.key
  }
  eb.send("keyWasPressed", JSON.stringify(keyPressedInfo))
})

function drawCanvas() {
  console.log("before drawCanvas()")
  charIndex = 0
  for(let y = 0; y < canvasHeight - 1; y++) {
    for(let x = 0; x < canvasWidth; x++) {
      console.log("cursor x = " + cursorX + ", cursor y = " + cursorY + "\nx = " + x + ", y = " + y)
      if(cursorX == x && cursorY == y) {
        drawCharacter(x, y, "O")
      }
      if(content.charAt(charIndex) === "\n") {
        y++;
        x = -1;
      } else {
        drawCharacter(x, y, content.charAt(charIndex))
      }
      charIndex++
    }
  }
  console.log("content = " + content + ". content length = " + content.length)
  console.log("after drawCanvas()")
}

let i = 0

function toX(x) {
  return x * cellWidth + canvasXOffset
}

function toY(y) {
  return y * cellHeight + canvasYOffset
}

function drawCharacter(x, y, characterToDraw) {
  console.log("before drawCharacter()")
  ctx.fillText(characterToDraw, toX(x), toY(y));
  console.log("after drawCharacter(), " + i + " characters have been drawn")
  i++
  console.log(i + " characters have been drawn")
}

function clearCanvas() {
  console.log("before clearCanvas()")
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  console.log("after clearCanvas()")
}
