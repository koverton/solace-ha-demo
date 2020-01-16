# mock-matcher

A demo fault-tolerant matching engine using my Solace-HA framework for state management and state transition across failovers.

## Demo Diagram

TBD

## Components

1. MockMatchingEngine : basic matching engine example that can be run as a member of FT-group
2. MockOrderGateway : basic randomized marketdata feed acround a mid-price to be sure to generate the occasional match
3. Monitor : Simple web-UI to visualize the output of the MockMatchingEngine instances

## Running the Demo

### Matcher Commandline

```bash
MockMatchingEngine <host> <vpn> <user> <pass> <app> <inst> <intopic> <peertopic> <activetopic> <standbytopic> <instr> <midpx>
    host: event broker address to connect to
    vpn:  messaging-VPN to connect to
    user: username for the connection
    pass: password for the connection
    app:  application name (used in group-communication topic)
    inst: instance id (integer)
    intopic: topic for input orders, e.g. order/new/AAPL
    peertopic: topic for output state from the active peer matching engine
    activetopic: additional telemtry feed for monitoring of the active member
    standbytopic: additional teleme feed for monitoring the standby members
    instrument: the instrument for the orderbook
    midpx: initial midprice for the stack
```

### Order Gateway Commandline

```bash
MockOrderGateway <host> <vpn> <user> <pass> <outtopic> <start-id> <symbol> <mid>
    host: event broker address to connect to
    vpn:  messaging-VPN to connect to
    user: username for the connection
    pass: password for the connection
    outtopic: topic for generated orders, e.g. order/new/AAPL
    startid: integer orderID to start with, monotonically increasing
    symbol: instrument symbol on which orders are generated
    midpx:  initial midprice for randomly generating prices, to make them more realistic
```




