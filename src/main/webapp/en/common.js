'use strict';

var API_ENDPOINT = '/tomcat/irma_sms_issuer/api/';

var MESSAGES = {
    'sending-sms': 'SMS message is being sent...',
    'sms-sent': 'Code has been sent! You will receive a message from number <strong>%number%</strong>.',
    'verifying-token': 'Code is being verified ...',
    'issuing-credential': 'Attribute has been issed ...',
    'second': '%n% seconds',
    'seconds': '%n% seconds',
    'minute': '%n% minute',
    'minutes': '%n% minutes',
    'hour': '%n% hour',
    'hours': '%n% hour',
    'phone-add-success': 'Phone number attribute has been added.',
    'phone-add-timeout': 'The session timed out. Please reload this page and try again.',
    'phone-add-cancel': 'Cancelled.',
    'phone-add-error': 'Adding the phone number attribute failed, unfortunately.',
    'error:internal': 'Internal error. Please contact IRMA if this happens moreoften.',
    'error:sending-sms': 'Sending the SMS message fails. Most likely this is problem in the IRMA system. Please contact IRMA if this happens moreoften.',
    'error:ratelimit': 'Please try again in %time%.',
    'error:cannot-validate-token': 'The code cannot be verified. Is there a typo?',
};

// This var is global, as we need it to verify a phone number.
var phone;

function onload() {
    $('#phone-form').on('submit', onSubmitPhone);
    $('#token-form').on('submit', onSubmitToken);

    // Is this a link from a SMS message?
    if (document.location.hash.substr(0, '#!verify:'.length) == '#!verify:') {
        verifyTokenFromURL();
    }
}

function verifyTokenFromURL() {
    // format: #!verify:+3112345678:ABCDEF
    var parts = document.location.hash.split(':');
    if (parts.length != 3) {
        return;
    }

    if ('history' in window) {
        // remove hash
        history.replaceState(null, '', document.location.pathname);
    }

    phone = parts[1];
    var token = parts[2];

    // Now do the same thing as when a user manually submitted the form
    // Enter the phone number
    $('#phone-form input[type=tel]').val(phone);
    $('#phone-form input').prop('disabled', true);

    // Get a token and fill it here
    $('#token-form input[type=text]').val(token);
    $('#block-token').show();

    // And submit!
    $('#token-form').submit();
}

function onSubmitPhone(e) {
    e.preventDefault();

    // Disable first field
    $('#phone-form input').prop('disabled', true);

    phone = $('#phone-form input[type=tel]').val().trim();
    setStatus('info', MESSAGES['sending-sms']);
    $.post(API_ENDPOINT + 'send', {phone: phone})
        .done(function(e) {
            console.log('sent SMS:', e);
            var parts = e.split(':');
            // parts[0] should be 'OK'
            var senderNumber = parts[1];
            setStatus('info', MESSAGES['sms-sent'].replace('%number%', senderNumber));
            $('#block-token').show();
        })
        .fail(function(e) {
            $('#phone-form input').prop('disabled', false);
            var errormsg = e.responseText;
            console.error('failed to submit phone number:', errormsg);
            if (!errormsg || errormsg.substr(0, 6) != 'error:') {
                errormsg = 'error:internal';
            }
            if (errormsg == 'error:ratelimit') {
                var retryAfter = e.getResponseHeader('Retry-After');
                // In JavaScript, we can mostly ignore the fact we're dealing
                // with a string here and treat it as an integer...
                if (retryAfter < 60) {
                    var timemsg = MESSAGES['seconds'];
                    if (retryAfter == 1) {
                        var timemsg = MESSAGES['second'];
                    }
                } else if (retryAfter < 60*60) {
                    retryAfter = Math.round(retryAfter / 60);
                    var timemsg = MESSAGES['minutes'];
                    if (retryAfter == 1) {
                        var timemsg = MESSAGES['minute'];
                    }
                } else {
                    retryAfter = Math.round(retryAfter / 60 / 60);
                    var timemsg = MESSAGES['hours'];
                    if (retryAfter == 1) {
                        var timemsg = MESSAGES['hour'];
                    }
                }
                setStatus('danger', MESSAGES[errormsg].replace('%time%',
                    timemsg.replace('%n%', retryAfter)));
            } else {
                setStatus('danger', MESSAGES[errormsg]);
            }
        });
}

function onSubmitToken(e) {
    e.preventDefault();
    var token = $('#token-form input[type=text]').val().trim().toUpperCase();
    setStatus('info', MESSAGES['verifying-token']);
    $.post(API_ENDPOINT + 'verify', {phone: phone, token: token})
        .done(function(jwt) {
            console.log('received JWT:', jwt);
            setStatus('info', MESSAGES['issuing-credential']);
            IRMA.issue(jwt, function(e) {
                setStatus('success', MESSAGES['phone-add-success'])
                console.log('phone added:', e)
            }, function(e) {
                console.warn('cancelled:', e);
                // TODO: don't interpret these strings, use error codes instead.
                if (e === 'Session timeout, please try again') {
                    setStatus('info', MESSAGES['phone-add-timeout'])
                } else { // e === 'User cancelled authentication'
                    setStatus('info', MESSAGES['phone-add-cancel'])
                }
            }, function(e) {
                setStatus('danger', MESSAGES['phone-add-error'])
                console.error('error:', e);
            })
        })
        .fail(function(e) {
            var errormsg = e.responseText;
            console.error('failed to submit phone number:', errormsg);
            if (!errormsg || errormsg.substr(0, 6) != 'error:') {
                errormsg = 'error:internal';
            }
            setStatus('danger', MESSAGES[errormsg]);
        });
}

// copied from irma_email_issuer
function setStatus(alertType, message) {
    $('#status')
        .removeClass('alert-success')
        .removeClass('alert-info')
        .removeClass('alert-warning')
        .removeClass('alert-danger')
        .addClass('alert-'+alertType)
        .html(message)
        .removeClass('hidden');
}

onload();
