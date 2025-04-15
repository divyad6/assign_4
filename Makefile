JAVAC = javac
SRC = src
CLASSES = $(SRC)/TCPend.java $(SRC)/Sender.java $(SRC)/Receiver.java $(SRC)/Packet.java $(SRC)/Utils.java

all:
	$(JAVAC) -d $(SRC) $(CLASSES)

clean:
	rm -f $(SRC)/*.class
