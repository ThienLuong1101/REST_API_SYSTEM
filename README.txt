How to run it: 

1. javac *.java
2.java AggregationServer //run the aggregation server
3. java MultiContentServers //run the content servers
4. java Client <your local url> // example: java Client http://localhost:4567


there are 3 main components: content server, aggregation server, and client

summary my application design:

I hae 3 instances of content server, these ramdomly send PUT requests to the aggregation server.
every content servers and aggregation server have their own lamport lock. 

(I set up for clients and content servers randomly send requests within 3 - 5 seconds to the aggregation server);

when content server sends PUT requests to the aggregation server, it also sends the lamport lock to aggregation server.

when the aggregation server receive the PUT requests, It will send a comfirm status "OK" back with the latest 
lamport lock time to the content server. So the content server know and update its lamport lock.

in my design and as my understanding of this assignment, the client only cares about the latest and consistency data. 
Therefore, I don't implement the lamport lock for clients, and focusing in sending the lastest data to them.



--- My implementation is focusing on delivering the "latest and consistency data" from the content server
to clients through the middleman - AggregationServer. ----