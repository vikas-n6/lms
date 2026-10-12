package com.lms.simulators.bank;

import com.lms.simulators.bank.BankSimulator.PennyDropResult;
import com.lms.simulators.bank.BankSimulator.StatementAnalysis;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sim/bank")
public class BankController {

    private final BankSimulator simulator;

    public BankController(BankSimulator simulator) {
        this.simulator = simulator;
    }

    @PostMapping("/penny-drop")
    public PennyDropResult pennyDrop(@RequestBody PennyDropRequest request) {
        return simulator.pennyDrop(request.accountNumber(), request.ifsc());
    }

    @PostMapping("/statement-analysis")
    public StatementAnalysis statementAnalysis(@RequestBody StatementRequest request) {
        return simulator.analyzeStatements(request.subjectId());
    }

    public record PennyDropRequest(@NotBlank String accountNumber, @NotBlank String ifsc) {}

    public record StatementRequest(@NotBlank String subjectId) {}
}
