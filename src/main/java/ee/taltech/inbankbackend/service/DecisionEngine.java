package ee.taltech.inbankbackend.service;

import com.github.vladislavgoltjajev.personalcode.locale.estonia.EstonianPersonalCodeValidator;
import ee.taltech.inbankbackend.config.DecisionEngineConstants;
import ee.taltech.inbankbackend.exceptions.InvalidLoanAmountException;
import ee.taltech.inbankbackend.exceptions.InvalidLoanPeriodException;
import ee.taltech.inbankbackend.exceptions.InvalidPersonalCodeException;
import ee.taltech.inbankbackend.exceptions.NoValidLoanException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;


/**
 * A service class that provides a method for calculating an approved loan amount and period for a customer.
 * The loan amount is calculated based on the customer's credit modifier,
 * which is determined by the last four digits of their ID code.
 */
@Service
public class DecisionEngine {

    // Used to check for the validity of the presented ID code.
    private final EstonianPersonalCodeValidator validator = new EstonianPersonalCodeValidator();
    private int creditModifier = 0;

    /**
     * Calculates the maximum loan amount and period for the customer based on their ID code,
     * the requested loan amount and the loan period.
     * The loan period must be between 12 and 60 months (inclusive).
     * The loan amount must be between 2000 and 10000€ months (inclusive).
     *
     * @param personalCode ID code of the customer that made the request.
     * @param loanAmount Requested loan amount
     * @param loanPeriod Requested loan period
     * @return A Decision object containing the approved loan amount and period, and an error message (if any)
     * @throws InvalidPersonalCodeException If the provided personal ID code is invalid
     * @throws InvalidLoanAmountException If the requested loan amount is invalid
     * @throws InvalidLoanPeriodException If the requested loan period is invalid
     * @throws NoValidLoanException If there is no valid loan found for the given ID code, loan amount and loan period
     */
    public Decision calculateApprovedLoan(String personalCode, Long loanAmount, int loanPeriod)
            throws InvalidPersonalCodeException, InvalidLoanAmountException, InvalidLoanPeriodException,
            NoValidLoanException {
        try {
            verifyInputs(personalCode, loanAmount, loanPeriod);
        } catch (Exception e) {
            return new Decision(null, null, e.getMessage(), null);
        }

        int outputLoanAmount;
        creditModifier = getCreditModifier(personalCode);

        if (!isClientAdult(personalCode)) {
            throw new NoValidLoanException("The minimum age for applying for a loan is 18.");
        }
        if (!isAgeValidForLoanLength(personalCode, loanPeriod)) {
            throw new NoValidLoanException("Unfortunately, your age exceeds the maximum set limit for this loan.");
        }
        if (creditModifier == 0) {
            throw new NoValidLoanException("No valid loan found!");
        }

        while (highestValidLoanAmount(loanPeriod) < DecisionEngineConstants.MINIMUM_LOAN_AMOUNT) {
            loanPeriod++;
        }

        if (loanPeriod <= DecisionEngineConstants.MAXIMUM_LOAN_PERIOD) {
            outputLoanAmount = Math.min(DecisionEngineConstants.MAXIMUM_LOAN_AMOUNT, highestValidLoanAmount(loanPeriod));

        } else {
            throw new NoValidLoanException("No valid loan found!");
        }

        int requestedLoanAmount = Math.toIntExact(loanAmount);

        int requestedMonthlyPayment = 0;
        if (requestedLoanAmount <= outputLoanAmount) {
            requestedMonthlyPayment = monthlyPaymentCalculator(requestedLoanAmount, loanPeriod, personalCode);

        }

        return new Decision(outputLoanAmount, loanPeriod, null, requestedMonthlyPayment);
    }

    /**
     * Calculates the monthly payment for the amount client requested.
     * Bank needs to make some money, so I added some interest.
     * If clients creditModifier is 100, interest rate is 5%, it creditModifier is 300, interest comes down to 4%
     * and if the client has a creditModifier of 1000 the interest rate comes down to 3%
     * @return Monthly payment
     */
    private int monthlyPaymentCalculator (int requestedLoanAmount, int loanPeriod, String personalCode) {

        int modifier = getCreditModifier(personalCode);
        double interest = switch (modifier) {
            case 100 -> DecisionEngineConstants.INTEREST_RATE_1;
            case 300 -> DecisionEngineConstants.INTEREST_RATE_2;
            case 1000 -> DecisionEngineConstants.INTEREST_RATE_3;
            default -> 0.0;
        };

        double beforeInterest = (double) requestedLoanAmount / loanPeriod;
        double interestCalc = beforeInterest + (beforeInterest * interest);

        return (int) interestCalc;

    }




    /**
     * Calculates clients birthday(LocalDate) from clients personal code.
     */
    private LocalDate birthDateGenerator(String personalCode) {

        String century = null;

        if(personalCode.charAt(0) == '3'||personalCode.charAt(0) == '4') {
            century = "19";
        }

        if(personalCode.charAt(0) == '5'||personalCode.charAt(0) == '6') {
            century = "20";
        }

        int year = Integer.parseInt(century + personalCode.substring(1,3));
        int month = Integer.parseInt(personalCode.substring(3,5));
        int day = Integer.parseInt(personalCode.substring(5,7));

        return LocalDate.of(year, month, day);

    }

    /**
     * Calculates clients age at the end on the loan contract.
     * If the persons age is above 78 years(935 months) with loan period is added,
     * the loan is declined.
     */
    private boolean isAgeValidForLoanLength(String personalCode, int loanPeriod) {
        LocalDate now = LocalDate.now();
        LocalDate birthDate = birthDateGenerator(personalCode);

        Period clientAge = Period.between(birthDate, now);

        int months = clientAge.getYears() * 12 + clientAge.getMonths();

        return (months + loanPeriod) <= DecisionEngineConstants.EST_MAX_AGE_MONTHS;
    }

    /**
     * Calculates if the client is an adult.
     */

    private boolean isClientAdult(String personalCode) {
        LocalDate now = LocalDate.now();

        LocalDate birthDate = birthDateGenerator(personalCode);
        LocalDate adultDate = now.minusYears(18);

        return !birthDate.isAfter(adultDate);

    }

    /**
     * Calculates the largest valid loan for the current credit modifier and loan period.
     *
     * @return Largest valid loan amount
     */
    private int highestValidLoanAmount(int loanPeriod) {
        return creditModifier * loanPeriod;
    }

    /**
     * Calculates the credit modifier of the customer to according to the last four digits of their ID code.
     * Debt - 0000...2499
     * Segment 1 - 2500...4999
     * Segment 2 - 5000...7499
     * Segment 3 - 7500...9999
     *
     * @param personalCode ID code of the customer that made the request.
     * @return Segment to which the customer belongs.
     */
    private int getCreditModifier(String personalCode) {
        int segment = Integer.parseInt(personalCode.substring(personalCode.length() - 4));

        if (segment < 2500) {
            return 0;
        } else if (segment < 5000) {
            return DecisionEngineConstants.SEGMENT_1_CREDIT_MODIFIER;
        } else if (segment < 7500) {
            return DecisionEngineConstants.SEGMENT_2_CREDIT_MODIFIER;
        }

        return DecisionEngineConstants.SEGMENT_3_CREDIT_MODIFIER;
    }

    /**
     * Verify that all inputs are valid according to business rules.
     * If inputs are invalid, then throws corresponding exceptions.
     *
     * @param personalCode Provided personal ID code
     * @param loanAmount Requested loan amount
     * @param loanPeriod Requested loan period
     * @throws InvalidPersonalCodeException If the provided personal ID code is invalid
     * @throws InvalidLoanAmountException If the requested loan amount is invalid
     * @throws InvalidLoanPeriodException If the requested loan period is invalid
     */
    private void verifyInputs(String personalCode, Long loanAmount, int loanPeriod)
            throws InvalidPersonalCodeException, InvalidLoanAmountException, InvalidLoanPeriodException {

        if (!validator.isValid(personalCode)) {
            throw new InvalidPersonalCodeException("Invalid personal ID code!");
        }
        if (!(DecisionEngineConstants.MINIMUM_LOAN_AMOUNT <= loanAmount)
                || !(loanAmount <= DecisionEngineConstants.MAXIMUM_LOAN_AMOUNT)) {
            throw new InvalidLoanAmountException("Invalid loan amount!");
        }
        if (!(DecisionEngineConstants.MINIMUM_LOAN_PERIOD <= loanPeriod)
                || !(loanPeriod <= DecisionEngineConstants.MAXIMUM_LOAN_PERIOD)) {
            throw new InvalidLoanPeriodException("Invalid loan period!");
        }

    }
}