<template>
  <v-card-text>
    <v-row justify="center">
      <v-col
        cols="12"
        md="6"
      >
        <v-date-picker
          id="appointment-datepicker"
          v-model="selectedDate"
          show-current
          light
          :allowed-dates="getAllowedDates"
          :events="availableDates"
          event-color="green lighten-1"
          color="success"
          header-color="primary"
          full-width
          @click:date="dateClicked('true')"
        ></v-date-picker>
      </v-col>
      <v-col
        cols="12"
        md="6"
        class="text-center"
      >
        <div v-if="selectedTimeSlot">
          <strong class="mr-1">Appointment Date: </strong>
          <br class='d-sm-none' />
          {{selectedDateFormatted}}, {{selectedTimeSlot}}
        </div>
        <div v-else>
          <strong class="mr-1">Date Selected: </strong> {{selectedDateFormatted}}
        </div>
        <template v-if="selectedDateTimeSlots.length">
          <div class="mt-6">
            <strong>Available Time Slots</strong>
          </div>
          <v-row>
            <v-col
              cols="12"
              sm="6"
              v-for="(timeslot, index) in selectedDateTimeSlots"
              :key="index"
            >
              <v-btn
                large
                outlined
                block
                @click="selectTimeSlot(timeslot)"
                color="primary">
                {{`${timeslot.startTimeStr} - ${timeslot.endTimeStr}`}}
              </v-btn>
            </v-col>
          </v-row>
        </template>
        <template v-else>
          <div class="mt-6 error-text">
            <strong>No time slots available on the selected date</strong>
          </div>
        </template>
      </v-col>
    </v-row>
  </v-card-text>
</template>

<script lang="ts">
import { Appointment, AppointmentSlot } from '@/models/appointment'
import CommonUtils, { timezoneOffset } from '@/utils/common-util'
import { Component, Mixins, Prop, Vue } from 'vue-property-decorator'
import { mapActions, mapMutations, mapState } from 'vuex'
import { utcToZonedTime, zonedTimeToUtc } from 'date-fns-tz'
import { Office } from '@/models/office'
import { OfficeModule } from '@/store/modules'
import { Service } from '../../models/service'
import StepperMixin from '@/mixins/StepperMixin.vue'
import { format } from 'date-fns'

@Component({
  computed: {
    ...mapState('office', [
      'availableAppointmentSlots',
      'currentAppointmentSlot',
      'currentOffice',
      'currentOfficeTimezone',
      'currentService'
    ])
  },
  methods: {
    ...mapMutations('office', [
      'setCurrentAppointmentSlot',
      'setCurrentDraftAppointment'
    ]),
    ...mapActions('office', [
      'getAvailableAppointmentSlots',
      'createDraftAppointment'
    ])
  }
})
export default class DateSelection extends Mixins(StepperMixin) {
  private readonly availableAppointmentSlots!: any
  private readonly currentOffice!: Office
  private readonly currentAppointmentSlot!: AppointmentSlot
  private readonly currentOfficeTimezone!: string
  private readonly getAvailableAppointmentSlots!: (input: {officeId: number, serviceId: number}) => Promise<any>
  private readonly createDraftAppointment!: () => Promise<any>
  private readonly setCurrentAppointmentSlot!: (slot: AppointmentSlot) => void
   private readonly setCurrentDraftAppointment!: (appointment: Appointment) => void
  private readonly currentService!: Service
  // TODO: take timezone from office data from state
  private selectedDate = ''
  private selectedDateObj = ''
  private selectedDateTimeSlots = []
  private availableDates = []
  private isUserClicked = 'false'

   private isLoading: boolean = false

   private get selectedDateFormatted () {
     if (this.isUserClicked === 'true') {
       return CommonUtils.getTzFormattedDate(new Date(CommonUtils.changeDateFormat(this.selectedDate)), Intl.DateTimeFormat().resolvedOptions().timeZone, 'MMM dd, yyyy')
     } else if (this.selectedDateObj) {
       return CommonUtils.getTzFormattedDate(new Date(this.selectedDateObj), Intl.DateTimeFormat().resolvedOptions().timeZone, 'MMM dd, yyyy')
     }
   }

   private get selectedTimeSlot () {
     return (this.currentAppointmentSlot?.start_time && this.currentAppointmentSlot?.end_time)
       ? `${CommonUtils.getUTCToTimeZoneTime(this.currentAppointmentSlot?.start_time, this.currentOfficeTimezone, 'hh:mm aaa')} -
        ${CommonUtils.getUTCToTimeZoneTime(this.currentAppointmentSlot?.end_time, this.currentOfficeTimezone, 'hh:mm aaa')}`
       : ''
   }

   private async mounted () {
     if (this.isOnCurrentStep) {
       if (this.currentOffice?.office_id) {
         this.getAvailableService()
       }
       this.dateClicked()
     }
   }

   private async getAvailableService () {
     const availableAppoinments = await this.getAvailableAppointmentSlots({
       officeId: this.currentOffice.office_id,
       serviceId: this.currentService.service_id
     })
     Object.keys(availableAppoinments).forEach(date => {
       if (availableAppoinments[date]?.length) {
         this.availableDates.push(CommonUtils.getTzFormattedDate(new Date(date), this.currentOfficeTimezone))
         if (!this.selectedDate) {
           this.selectedDate = CommonUtils.getTzFormattedDate(new Date(date), this.currentOfficeTimezone)
           this.selectedDateObj = date
           this.dateClicked()
         }
       }
     })
   }
   private getAllowedDates (val) {
     return this.availableDates.find(date => date === val)
   }

   private dateClicked (userClicked = 'false') {
     this.selectedDateTimeSlots = []
     let slots = []
     if (this.selectedDate) {
       if (userClicked === 'true') {
         this.isUserClicked = 'true'
         slots = this.availableAppointmentSlots[CommonUtils.getTzFormattedDate(new Date(CommonUtils.changeDateFormat(this.selectedDate)), this.currentOfficeTimezone, 'MM/dd/yyyy')]
       } else {
         slots = this.availableAppointmentSlots[CommonUtils.getTzFormattedDate(new Date(this.selectedDateObj), this.currentOfficeTimezone, 'MM/dd/yyyy')]
       }
     }
     slots?.forEach(slot => {
       this.selectedDateTimeSlots.push({
         ...slot,
         startTimeStr: CommonUtils.get12HTimeString(slot.start_time),
         endTimeStr: CommonUtils.get12HTimeString(slot.end_time)
       })
     })
   }

   async selectTimeSlot (slot) {
     // Note - For cross browser, we must use specific date string format below
     // Chrome/FF pass with "2020-05-08 09:00" but Safari fails.
     // Safari needs format from spec, "2020-05-08T09:00-07:00"
     // (safari also needs timezone offset)
     const selectedSlot: AppointmentSlot = {
       // start_time: new Date(`${this.selectedDate}T${slot.start_time}${timezoneOffset()}`).toISOString(),
       // end_time: new Date(`${this.selectedDate}T${slot.end_time}${timezoneOffset()}`).toISOString()
       start_time: zonedTimeToUtc(new Date(`${this.selectedDate}T${slot.start_time}`), this.currentOfficeTimezone).toISOString(),
       end_time: zonedTimeToUtc(new Date(`${this.selectedDate}T${slot.end_time}`), this.currentOfficeTimezone).toISOString()
     }
     this.setCurrentAppointmentSlot(selectedSlot)
     // this.createDraftAppointment()
     // this.isLoading = true
     try {
       const resp = await this.createDraftAppointment()
       //  if (resp.appointment_id) {
       if (resp) {
         this.setCurrentDraftAppointment(resp)
         this.stepNext()
         // this.isLoading = false
       }
     } catch (error) {
       this.isLoading = false

       this.getAvailableService()
       this.dateClicked()
       // this.dialogPopup.showDialog = true
       // this.dialogPopup.isSuccess = false
       // this.dialogPopup.title = 'Failed!'
       // this.dialogPopup.subTitle = error?.response?.data?.message || 'Unable to book the appointment.'
     }
   }
}
</script>

<style lang="scss" scoped>
@import "@/assets/scss/theme.scss";
@import "@/assets/scss/overrides.scss";
</style>
