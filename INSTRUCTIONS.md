## Instructions to run the project

1. On the root of the project run `docker compose up --build`
2. A container group will be spun up, once all containers are Online, head to `http://localhost:5173` where you'll find the dashboard.
   
In this dashboard you can:
- Create a single order
- Create a bulk of orders
- Run a 5 minute run of 10 order bulks, to verify the second experiment

You'll be able to see the realtime state of assembly and transport orders as well as the messages being sent around after every ack.

There will be a postgres container which you can access with credentials:

`mfps:mfps` in which you can find all logged data.