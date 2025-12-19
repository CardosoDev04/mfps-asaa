# The Queries

To verify this project we consider the following queries:

- `A[] not deadlock`
- `A[] (assembly.ReceivingConfirmation imply assembly.confirmation_clock <= 2)`
- `A[] (global_clock >= 300 imply assembly_orders_sent >= 30)`