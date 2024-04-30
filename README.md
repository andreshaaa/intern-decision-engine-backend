# TICKET-101 

List of things that I discovered: 
## Issue No. 1: 

In Ticket-101 it was described that the decision engine should determine what would be the maximum sum, regardless of the person's requested loan amount: 
“For example, if a person applies for €4000,-, but we determine that we would approve a larger sum then the result should be the maximum sum which we would approve.” 

Out of the box the app's “Approved Loan amount” matches the amount that the customer requested in the slider. 
And it does that until the “Approved Loan amount” reaches the approved max amount from the backend, 
then stops at the maximum approved sum.

It however does not determine and display what would be the maximum loan amount in any other position except when the customer has surpassed the maximum amount. 
To fix this I removed an if-else statement from line 41 in frontend. 

Reasons for that: 
The way it was set up was that if the customer requested less than the backend offered (the bank's maximum approved amount) the frontend returned the customer's requested amount. 
Only when the customer requested amount exceeded the backends (banks) allowed amount, did the “Approved amount” field display backends response amount. 

I do not see any reason for displaying customers selected values (on the sliders) on the bank's approved fields. Approved fields should only show what the backend returns (bank approves). 
This brings us to the next issue: 

## Issue No. 2: 
If the decision engine works as the task acquired: 

“For example, if a person applies for €4000,-, but we determine that we would approve a larger sum, then the result should be the maximum sum which we would approve.“ 
then the upper “Loan amount” slider becomes almost useless,
as the backend calculates the maximum allowed amount only from personal code, keeps it between min/max and returns it modified only by the loan length (in months). 

In this case, it now does not make sense to even send the requested loan amount to the backend because the backend only returns the maximum amount depending on loan length. 
The upper “Loan amount” slider would make sense if there was some sort of “continue” button or if the engine calculated monthly payments. 

## Issue No. 3: 
Lack of ability to calculate monthly payments.

I'm gonna discuss this even when it was not mentioned/required in the ticket. 
Every person would want to know what the monthly loan payment would be. It would be the most important factor in evaluating one's ability to meet monthly payments. 
Backends (banks) calculation of monthly payments would also justify the presence of a “Loan amount” slider. 

## Issue No. 4: 

In the assignment, there is a constraint: “Minimum loan period can be 12 months” but the slider starts with 6 months. It should be 12. 

## What I improved in TICKET-101: 

Frontend now only shows max amount of allowable loan from the bank (backend). (Issue No. 1) Slider now starts at 12 months. (Issue No. 4) 
I also implemented the ability to calculate and display monthly loan payments. More on that under “Extra”. (Issues No. 2 & 3) 

## TICKET-102 

Age restrictions 

I added age restrictions as requested. If the customer's personal code indicates a minor, the customer's loan request will be refused with the following message: 
“The minimum age for applying for a loan is 18.”. 

If the customer's age or age + loan length is more than the expected average lifespan, the loan request will be refused with the following message: 
“Unfortunately, your age exceeds the maximum set limit for this loan.”. 

## EXTRA STUFF aka THE MAIN PROBLEM 

I think it's very important for the client to know how much the monthly payment would be for the requested loan amount. The app currently does not display that. 

So I took the opportunity and implemented the possibility for the customer to see the monthly payment for the selected loan amount. 
And so, the loan amount slider in FE now has more to do. 
As any bank needs to earn money, I also took the liberty and added an interest rate to the monthly payment. 

Added interest rate works as follows: 
If the customer's creditModifier is 100, the interest rate is 5%, if the creditModifier is 300,
interest comes down to 4% and if the client has a creditModifier of 1000 the interest rate comes down to 3%. 
And the interest is added and shown as a part of the monthly payment. 




# InBank Backend Service

This service provides a REST API for calculating an approved loan amount and period for a customer.
The loan amount is calculated based on the customer's credit modifier, which is determined by the last four
digits of their ID code.

## Technologies Used

- Java 17
- Spring Boot
- [estonian-personal-code-validator:1.6](https://github.com/vladislavgoltjajev/java-personal-code)

## Requirements

- Java 17
- Gradle

## Installation

To install and run the service, please follow these steps:

1. Clone the repository.
2. Navigate to the root directory of the project.
3. Run `gradle build` to build the application.
4. Run `java -jar build/libs/inbank-backend-1.0.jar` to start the application

The default port is 8080.

## Endpoints

The application exposes a single endpoint:

### POST /loan/decision

The request body must contain the following fields:

- personalCode: The customer's personal ID code.
- loanAmount: The requested loan amount.
- loanPeriod: The requested loan period.

**Request example:**

```json
{
"personalCode": "50307172740",
"loanAmount": "5000",
"loanPeriod": "24"
}
```

The response body contains the following fields:

- loanAmount: The approved loan amount.
- loanPeriod: The approved loan period.
- errorMessage: An error message, if any.

**Response example:**

```json
{
"loanAmount": 2400,
"loanPeriod": 24,
"errorMessage": null
}
```

## Error Handling

The following error responses can be returned by the service:

- `400 Bad Request` - in case of an invalid input
    - `Invalid personal ID code!` - if the provided personal ID code is invalid
    - `Invalid loan amount!` - if the requested loan amount is invalid
    - `Invalid loan period!` - if the requested loan period is invalid
- `404 Not Found` - in case no valid loans can be found
    - `No valid loan found!` - if there is no valid loan found for the given ID code, loan amount, and loan period
- `500 Internal Server Error` - in case the server encounters an unexpected error while processing the request
    - `An unexpected error occurred` - if there is an unexpected error while processing the request

## Architecture

The service consists of two main classes:

- DecisionEngine: A service class that provides a method for calculating an approved loan amount and period for a customer.
- DecisionEngineController: A REST endpoint that handles requests for loan decisions.
