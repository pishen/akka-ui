# AkkaUI

Build your reactive UI using [Akka.js](https://github.com/akka-js/akka.js)

``` scala
implicit val system = ActorSystem("AkkaUI")
implicit val materializer = ActorMaterializer()

val textBox = input(placeholder := "Name").render
val name = span().render

val source: Source[Event, NotUsed] = textBox.source(_.oninput_=)
val sink: Sink[String, NotUsed] = name.sink(_.textContent_=)

source.map(_ => textBox.value).runWith(sink)

val root = div(
  textBox,
  h2("Hello ", name)
)

document.querySelector("#root").appendChild(root.render)
```
